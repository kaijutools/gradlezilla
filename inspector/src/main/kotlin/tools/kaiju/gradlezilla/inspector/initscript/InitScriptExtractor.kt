package tools.kaiju.gradlezilla.inspector.initscript

import org.gradle.tooling.GradleConnectionException
import tools.kaiju.gradlezilla.models.AgpDataExtractor
import tools.kaiju.gradlezilla.models.ExtractionContext
import tools.kaiju.gradlezilla.models.ExtractionOutcome
import tools.kaiju.gradlezilla.models.InitScriptOutputParser
import tools.kaiju.gradlezilla.models.JdkFactsParser
import tools.kaiju.gradlezilla.models.ModuleJdkFacts
import tools.kaiju.gradlezilla.models.rootCause
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
                .withArguments(buildInitScriptArguments(initScriptFile))
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
            ExtractionOutcome.Failed("Failed to extract with init script: ${e.rootCause().message}", e)
        } catch (e: GradleConnectionException) {
            dumpDebugOutput(outputStream.toString(), errorStream.toString())
            ExtractionOutcome.Failed("Failed to extract with init script: ${e.rootCause().message}", e)
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

/** Must match the property name `extractor.gradle` reads at script top level. */
internal const val CACHE_BUST_PROPERTY = "gradlezillaCacheBust"

/**
 * The configuration cache lives in the target project's own directory, not our isolated Gradle
 * user home — so a pre-existing entry (from the project's own dev workflow, CI, or a prior
 * gradlezilla run) can silently skip the whole configuration phase, including this init script's
 * data-emitting hooks, on any run after the first.
 *
 * Disabling configuration cache outright (`--no-configuration-cache`) is not an option: Gradle's
 * Isolated Projects feature *mandates* configuration cache and hard-fails
 * ("Configuration Cache cannot be disabled when Isolated Projects is enabled") if you try —
 * confirmed against a real Isolated-Projects project. Instead, a fresh random value is passed as
 * a system property on every run; `extractor.gradle` reads it via `providers.systemProperty(...)`
 * at script top level, which Gradle tracks as a configuration-cache input. A changed input always
 * forces a full reconfiguration — busting the cache on every run without ever disabling it, so it
 * works whether or not Isolated Projects is on, with no Gradle-version gating needed.
 */
internal fun buildInitScriptArguments(initScriptFile: File): List<String> =
    listOf(
        "--init-script",
        initScriptFile.absolutePath,
        "-q",
        "-D$CACHE_BUST_PROPERTY=${UUID.randomUUID()}",
    )
