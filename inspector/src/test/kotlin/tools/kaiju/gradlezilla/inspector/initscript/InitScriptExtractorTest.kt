package tools.kaiju.gradlezilla.inspector.initscript

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InitScriptExtractorTest {
    private val initScriptFile = File("/tmp/gradlezilla-ext-test.gradle")

    @Test
    fun buildInitScriptArguments_includesInitScriptAndQuiet() {
        val args = buildInitScriptArguments(initScriptFile)
        assertTrue(args.containsAll(listOf("--init-script", initScriptFile.absolutePath, "-q")))
    }

    /**
     * `--project-cache-dir` and the configuration-cache opt-outs now live in
     * `PinnedConnection.pinnedArguments` and are appended to, never replaced — re-adding them
     * here would be the old footgun (and would duplicate them on the command line).
     */
    @Test
    fun buildInitScriptArguments_carriesNoConnectionLevelArguments() {
        val args = buildInitScriptArguments(initScriptFile)
        assertFalse(args.contains("--project-cache-dir"), "connection-level argument leaked: $args")
        assertFalse(args.contains("--no-configuration-cache"), "connection-level argument leaked: $args")
    }

    /**
     * The per-run cache-bust token is gone; extraction now disables the configuration cache
     * outright. A token would silently reintroduce a fresh config-cache entry per run.
     */
    @Test
    fun buildInitScriptArguments_areStableAcrossCalls() {
        assertEquals(buildInitScriptArguments(initScriptFile), buildInitScriptArguments(initScriptFile))
    }

    @Test
    fun buildInitScriptArguments_carryNoCacheBustToken() {
        val args = buildInitScriptArguments(initScriptFile)
        assertFalse(
            args.any { it.contains("CacheBust", ignoreCase = true) },
            "expected no cache-bust token, got: $args",
        )
    }

    /**
     * The init script path is a fresh temp file every run. Leaking it into
     * `extractionMetadata.extractionArgs` — which is serialized into `--format json` — would break
     * the byte-identical-across-runs guarantee `e2e/determinism.sh` enforces. This regressed once
     * already, caught by running nowinandroid three times.
     */
    @Test
    fun redactInitScriptPath_replacesTheVolatileTempPath() {
        val args = listOf("--project-cache-dir", "/cache", "--init-script", initScriptFile.absolutePath, "-q")
        val redacted = redactInitScriptPath(args, initScriptFile)
        assertFalse(
            redacted.contains(initScriptFile.absolutePath),
            "volatile init script path survived redaction: $redacted",
        )
        assertEquals(
            listOf("--project-cache-dir", "/cache", "--init-script", INIT_SCRIPT_PLACEHOLDER, "-q"),
            redacted,
        )
    }

    @Test
    fun redactInitScriptPath_leavesEveryOtherArgumentAlone() {
        val args = listOf("--no-configuration-cache", "-Dorg.gradle.unsafe.isolated-projects=false")
        assertEquals(args, redactInitScriptPath(args, initScriptFile))
    }
}
