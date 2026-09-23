package tools.kaiju.gradlezilla.inspector.initscript

import org.gradle.tooling.GradleConnectionException
import tools.kaiju.gradlezilla.models.AgpDataExtractor
import tools.kaiju.gradlezilla.models.ExtractionContext
import tools.kaiju.gradlezilla.models.ExtractionOutcome
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
                .withArguments(buildInitScriptArguments(initScriptFile, context.connection.projectCacheDir))
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
            // Reason stays generic — GradleProjectInspector.executeExtractionChain appends the
            // root cause message once, centrally; embedding it here too would duplicate it.
            ExtractionOutcome.Failed("Failed to extract with init script", e)
        } catch (e: GradleConnectionException) {
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
 * `--project-cache-dir` points configuration-cache entries, task-execution history, etc. at our
 * own isolated, persistent [PinnedConnection.projectCacheDir] instead of the target project's own
 * `.gradle` — see [GradlezillaHome.projectCacheDir]. That directory is persistent, not temp
 * (so repeat runs against the same project stay fast), which means a config-cache entry from a
 * prior gradlezilla run against this same project can still sit there — so a pre-existing entry
 * can silently skip the whole configuration phase, including this init script's data-emitting
 * hooks, on any run after the first.
 *
 * Disabling configuration cache outright (`--no-configuration-cache`) is not an option: Gradle's
 * Isolated Projects feature *mandates* configuration cache and hard-fails
 * ("Configuration Cache cannot be disabled when Isolated Projects is enabled") if you try —
 * confirmed against a real Isolated-Projects project. Instead, a fresh random value is passed as
 * a system property on every run; `extractor.gradle` reads it via `providers.systemProperty(...)`
 * at script top level, which Gradle tracks as a configuration-cache input. A changed input always
 * forces a full reconfiguration — busting the cache on every run without ever disabling it, so it
 * works whether or not Isolated Projects is on, with no Gradle-version gating needed. Confirmed
 * empirically that this is still required even with an isolated project-cache-dir: with the
 * cache-bust value held fixed across runs, the *second* run against the same (persistent)
 * project-cache-dir reused the cached entry and produced no data line at all.
 *
 * `withArguments` replaces rather than accumulates prior calls, so `--project-cache-dir` is
 * re-added here alongside the init script's own arguments rather than relying on
 * [PinnedConnection]'s base setup.
 */
internal fun buildInitScriptArguments(
    initScriptFile: File,
    projectCacheDir: File,
): List<String> =
    listOf(
        "--init-script",
        initScriptFile.absolutePath,
        "--project-cache-dir",
        projectCacheDir.absolutePath,
        "-q",
        "-D$CACHE_BUST_PROPERTY=${UUID.randomUUID()}",
    )
