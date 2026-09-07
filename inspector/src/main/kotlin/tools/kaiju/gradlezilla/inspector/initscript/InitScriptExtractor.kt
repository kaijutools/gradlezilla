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
            parseOutput(output)?.let {
                ExtractionOutcome.Found(it)
            } ?: ExtractionOutcome.NotApplicable("Project misconfigured")
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

    private fun parseOutput(output: String): AgpData? {
        val lines = output.lines().filter { it.startsWith(DATA_PREFIX) }

        if (lines.isEmpty()) return null

        val dataLine = lines.first().removePrefix(DATA_PREFIX)

        val properties =
            dataLine.split("::").associate {
                val (key, value) = it.split("=")
                key to value.takeIf { v -> v != "null" }
            }

        val rawSdk = properties["compileSdk"]?.substringAfterLast("-")
        val compileSdk = rawSdk?.toIntOrNull() ?: return null

        return AgpData(
            compileSdk = compileSdk,
            buildToolsVersion = properties["buildTools"],
            ndkVersion = properties["ndk"],
        )
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
