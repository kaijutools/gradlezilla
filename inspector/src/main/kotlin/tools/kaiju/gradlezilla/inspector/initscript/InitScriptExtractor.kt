package tools.kaiju.gradlezilla.inspector.initscript

import tools.kaiju.gradlezilla.models.AgpData
import tools.kaiju.gradlezilla.models.AgpDataExtractor
import tools.kaiju.gradlezilla.models.ExtractionContext
import tools.kaiju.gradlezilla.models.ExtractionOutcome
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*

class InitScriptExtractor : AgpDataExtractor {
    override val name: String
        get() = InitScriptExtractor::class.java.canonicalName

    override fun extract(context: ExtractionContext): ExtractionOutcome {
        val initScriptFile = createInitScript()
        val outputStream = ByteArrayOutputStream()
        val errorStream = ByteArrayOutputStream()

        return try {
            context.connection
                .newBuild()
                .forTasks("help")
                .withArguments(
                    "--init-script",
                    initScriptFile.absolutePath,
                    "--no-configuration-cache",
                    "-q",
                ).setStandardOutput(outputStream)
                .setStandardError(errorStream)
                .run()

            val output = outputStream.toString()
            dumpDebugOutput(output, errorStream.toString())
            when (val result = parseOutput(output)) {
                is ParseOutcome.Success -> ExtractionOutcome.Found(result.data)
                is ParseOutcome.NoDataLine ->
                    ExtractionOutcome.NotApplicable(
                        "Init script produced no $DATA_PREFIX line — android extension not found on any project",
                    )
                is ParseOutcome.MissingCompileSdk ->
                    ExtractionOutcome.NotApplicable(
                        "Init script data line present but compileSdk is missing: '${result.dataLine}'",
                    )
                is ParseOutcome.UnparseableCompileSdk ->
                    ExtractionOutcome.Failed(
                        "Could not parse compileSdk value '${result.rawValue}' from init script output",
                        null,
                    )
            }
        } catch (e: Exception) {
            dumpDebugOutput(outputStream.toString(), errorStream.toString())
            ExtractionOutcome.Failed("Failed to extract with init script", e)
        } finally {
            initScriptFile.delete()
        }
    }

    private fun dumpDebugOutput(
        stdout: String,
        stderr: String,
    ) {
        if (System.getenv("GRADLEZILLA_DEBUG") == null) return
        System.err.println("[InitScriptExtractor] init script stdout:\n$stdout")
        System.err.println("[InitScriptExtractor] init script stderr:\n$stderr")
    }

    private fun parseOutput(output: String): ParseOutcome {
        val lines = output.lines().filter { it.startsWith(DATA_PREFIX) }

        if (lines.isEmpty()) return ParseOutcome.NoDataLine

        val dataLine = lines.first().removePrefix(DATA_PREFIX)

        val properties =
            dataLine.split("::").associate {
                val (key, value) = it.split("=")
                key to value.takeIf { v -> v != "null" }
            }

        val rawSdk = properties["compileSdk"] ?: return ParseOutcome.MissingCompileSdk(dataLine)
        val compileSdk =
            rawSdk.substringAfterLast("-").toIntOrNull()
                ?: return ParseOutcome.UnparseableCompileSdk(rawSdk)

        return ParseOutcome.Success(
            AgpData(
                compileSdk = compileSdk,
                buildToolsVersion = properties["buildTools"],
                ndkVersion = properties["ndk"],
            ),
        )
    }

    private sealed class ParseOutcome {
        data class Success(
            val data: AgpData,
        ) : ParseOutcome()

        data object NoDataLine : ParseOutcome()

        data class MissingCompileSdk(
            val dataLine: String,
        ) : ParseOutcome()

        data class UnparseableCompileSdk(
            val rawValue: String,
        ) : ParseOutcome()
    }

    @Throws(IllegalArgumentException::class)
    private fun createInitScript(): File {
        val rawScript =
            this::class.java.getResource("/extractor.gradle")?.readText()
                ?: error("Fatal: extractor.gradle not found in resources")

        val processedScript = rawScript.replace(PREFIX_TAG, DATA_PREFIX)

        return File.createTempFile("gradlezilla-ext-${UUID.randomUUID()}", ".gradle").apply {
            writeText(processedScript)
        }
    }

    private companion object {
        private const val PREFIX_TAG = "{{PREFIX}}"
        private const val DATA_PREFIX = "GRADLEZILLA_AGP_DATA::"
    }
}
