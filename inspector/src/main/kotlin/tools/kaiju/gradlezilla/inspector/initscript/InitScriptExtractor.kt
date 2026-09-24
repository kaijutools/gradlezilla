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

    /**
     * Populated as a side effect of [extract] — the argument list the extraction ran with, with
     * the init script's own path replaced by [INIT_SCRIPT_PLACEHOLDER]. That path is a freshly
     * named temp file every run, and this list is serialized into `--format json` output, which
     * `e2e/determinism.sh` asserts is byte-identical across consecutive runs.
     */
    var extractionArgs: List<String> = emptyList()
        private set

    override fun extract(context: ExtractionContext): ExtractionOutcome {
        val initScriptFile = createInitScript()
        val outputStream = ByteArrayOutputStream()
        val errorStream = ByteArrayOutputStream()
        val initScriptArgs = buildInitScriptArguments(initScriptFile)
        extractionArgs = redactInitScriptPath(context.connection.pinnedArguments + initScriptArgs, initScriptFile)

        return try {
            context.connection
                .build(initScriptArgs)
                .forTasks("help")
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

internal const val INIT_SCRIPT_PLACEHOLDER = "<generated init script>"

/**
 * The init script is written to a freshly named temp file on every run, so its path must never
 * reach `--format json` output — `e2e/determinism.sh` asserts that output is byte-identical
 * across consecutive runs of the same project.
 */
internal fun redactInitScriptPath(
    args: List<String>,
    initScriptFile: File,
): List<String> = args.map { if (it == initScriptFile.absolutePath) INIT_SCRIPT_PLACEHOLDER else it }

/**
 * Only the arguments specific to *this* extractor. Everything that must hold for every operation
 * on the connection — the isolated `--project-cache-dir` and the configuration-cache /
 * Isolated-Projects opt-outs that keep the configuration phase (and so these data hooks) from
 * being skipped — lives in [PinnedConnection.pinnedArguments] and is appended to by
 * [PinnedConnection.build], never replaced. See [PinnedConnection.pinnedArguments] for why the
 * cache is disabled rather than busted with a per-run token.
 */
internal fun buildInitScriptArguments(initScriptFile: File): List<String> =
    listOf(
        "--init-script",
        initScriptFile.absolutePath,
        "-q",
    )
