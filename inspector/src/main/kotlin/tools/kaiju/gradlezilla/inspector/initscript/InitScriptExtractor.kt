package tools.kaiju.gradlezilla.inspector.initscript

import tools.kaiju.gradlezilla.models.AgpDataExtractor
import tools.kaiju.gradlezilla.models.ExtractionContext
import tools.kaiju.gradlezilla.models.ExtractionOutcome
import tools.kaiju.gradlezilla.models.InitScriptOutputParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*

class InitScriptExtractor : AgpDataExtractor {
    override val name: String
        get() = InitScriptExtractor::class.java.simpleName

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
                    "-q",
                ).setStandardOutput(outputStream)
                .setStandardError(errorStream)
                .run()

            val output = outputStream.toString()
            dumpDebugOutput(output, errorStream.toString())
            when (val result = InitScriptOutputParser.parse(output)) {
                is InitScriptOutputParser.ParseOutcome.Success -> ExtractionOutcome.Found(result.data)
                is InitScriptOutputParser.ParseOutcome.NoDataLine ->
                    ExtractionOutcome.NotApplicable(
                        "Init script produced no ${InitScriptOutputParser.DATA_PREFIX} line — " +
                            "android extension not found on any project",
                    )
                is InitScriptOutputParser.ParseOutcome.MissingCompileSdk ->
                    ExtractionOutcome.NotApplicable(
                        "Init script data line present but compileSdk is missing: '${result.dataLine}'",
                    )
                is InitScriptOutputParser.ParseOutcome.UnparseableCompileSdk ->
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

    @Throws(IllegalArgumentException::class)
    private fun createInitScript(): File {
        val rawScript =
            this::class.java.getResource("/extractor.gradle")?.readText()
                ?: error("Fatal: extractor.gradle not found in resources")

        val processedScript = rawScript.replace(PREFIX_TAG, InitScriptOutputParser.DATA_PREFIX)

        return File.createTempFile("gradlezilla-ext-${UUID.randomUUID()}", ".gradle").apply {
            writeText(processedScript)
        }
    }

    private companion object {
        private const val PREFIX_TAG = "{{PREFIX}}"
    }
}
