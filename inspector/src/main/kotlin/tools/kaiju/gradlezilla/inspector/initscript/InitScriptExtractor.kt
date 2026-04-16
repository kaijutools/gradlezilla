package tools.kaiju.gradlezilla.inspector.initscript

import org.gradle.tooling.GradleConnector
import tools.kaiju.gradlezilla.models.AgpData
import tools.kaiju.gradlezilla.models.AgpDataExtractor
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*

class InitScriptExtractor : AgpDataExtractor {
    override fun extract(projectDir: File): AgpData? {
        val initScriptFile = createInitScript()
        val outputStream = ByteArrayOutputStream()

        return try {
            GradleConnector
                .newConnector()
                .forProjectDirectory(projectDir)
                .connect()
                .use { connection ->
                    connection
                        .newBuild()
                        .forTasks("help")
                        .withArguments(
                            "--init-script",
                            initScriptFile.absolutePath,
                            "--no-configuration-cache",
                            "-q",
                        ).setStandardOutput(outputStream)
                        .run()
                }

            val output = outputStream.toString()
            parseOutput(output)
        } catch (_: Exception) {
            null
        } finally {
            initScriptFile.delete()
        }
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
