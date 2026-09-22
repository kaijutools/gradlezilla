package tools.kaiju.gradlezilla.inspector.initscript

import tools.kaiju.gradlezilla.models.AgpDataExtractor
import tools.kaiju.gradlezilla.models.ExtractionContext
import tools.kaiju.gradlezilla.models.ExtractionOutcome
import tools.kaiju.gradlezilla.models.GradleVersion
import tools.kaiju.gradlezilla.models.InitScriptOutputParser
import tools.kaiju.gradlezilla.models.JdkFactsParser
import tools.kaiju.gradlezilla.models.ModuleJdkFacts
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.*

class InitScriptExtractor : AgpDataExtractor {
    override val name: String
        get() = InitScriptExtractor::class.java.simpleName

    /** Populated as a side effect of [extract] — the per-module JDK facts from the same run. */
    var jdkFacts: List<ModuleJdkFacts> = emptyList()
        private set

    override fun extract(context: ExtractionContext): ExtractionOutcome {
        val initScriptFile = createInitScript()
        val outputStream = ByteArrayOutputStream()
        val errorStream = ByteArrayOutputStream()

        return try {
            context.connection
                .build()
                .forTasks("help")
                .withArguments(buildArguments(initScriptFile, context.environment.gradleVersion))
                .setStandardOutput(outputStream)
                .setStandardError(errorStream)
                .run()

            val output = outputStream.toString()
            dumpDebugOutput(output, errorStream.toString())
            jdkFacts = JdkFactsParser.parse(output)
            when (val result = InitScriptOutputParser.parse(output)) {
                is InitScriptOutputParser.ParseOutcome.Success -> {
                    ExtractionOutcome.Found(result.data)
                }

                is InitScriptOutputParser.ParseOutcome.NoDataLine -> {
                    ExtractionOutcome.NotApplicable(
                        "Init script produced no ${InitScriptOutputParser.DATA_PREFIX} line — " +
                            "android extension not found on any project",
                    )
                }

                is InitScriptOutputParser.ParseOutcome.MissingCompileSdk -> {
                    ExtractionOutcome.NotApplicable(
                        "Init script data line present but compileSdk is missing: '${result.dataLine}'",
                    )
                }

                is InitScriptOutputParser.ParseOutcome.UnparseableCompileSdk -> {
                    ExtractionOutcome.Failed(
                        "Could not parse compileSdk value '${result.rawValue}' from init script output",
                        null,
                    )
                }
            }
        } catch (e: IOException) {
            dumpDebugOutput(outputStream.toString(), errorStream.toString())
            ExtractionOutcome.Failed("Failed to extract with init script", e)
        } finally {
            initScriptFile.delete()
        }
    }

    /**
     * The configuration cache lives in the target project's own directory, not our isolated
     * Gradle user home — so a pre-existing entry (from the project's own dev workflow, CI, or a
     * prior gradlezilla run) can silently skip the whole configuration phase, including this
     * init script's data-emitting hooks, on any run after the first. `--no-configuration-cache`
     * guarantees this build always reconfigures, but only exists from Gradle 6.6 onward — passing
     * it to an older Gradle would fail the build outright, so it's added conditionally.
     */
    private fun buildArguments(
        initScriptFile: File,
        gradleVersion: String,
    ): List<String> {
        val base = listOf("--init-script", initScriptFile.absolutePath, "-q")
        return if (supportsConfigurationCacheFlag(gradleVersion)) base + "--no-configuration-cache" else base
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

        val processedScript =
            rawScript
                .replace(PREFIX_TAG, InitScriptOutputParser.DATA_PREFIX)
                .replace(JDK_PREFIX_TAG, JdkFactsParser.DATA_PREFIX)

        return File.createTempFile("gradlezilla-ext-${UUID.randomUUID()}", ".gradle").apply {
            writeText(processedScript)
        }
    }

    private companion object {
        private const val PREFIX_TAG = "{{PREFIX}}"
        private const val JDK_PREFIX_TAG = "{{JDK_PREFIX}}"
    }
}

/** --configuration-cache/--no-configuration-cache: incubating since Gradle 6.6, stable in 8.1. */
@Suppress("MagicNumber")
private val MIN_CONFIGURATION_CACHE_FLAG_VERSION = GradleVersion(6, 6)

/** Fails open (omits the flag) when [gradleVersion] can't be parsed, matching GradleJdkCompatibility. */
internal fun supportsConfigurationCacheFlag(gradleVersion: String): Boolean =
    GradleVersion.parse(gradleVersion)?.let { it >= MIN_CONFIGURATION_CACHE_FLAG_VERSION } ?: false
