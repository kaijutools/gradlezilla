package tools.kaiju.gradlezilla.inspector.initscript

import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class InitScriptExtractorTest {
    private val initScriptFile = File("/tmp/gradlezilla-ext-test.gradle")
    private val projectCacheDir = File("/tmp/gradlezilla-project-cache-test")

    @Test
    fun buildInitScriptArguments_includesInitScriptAndQuiet() {
        val args = buildInitScriptArguments(initScriptFile, projectCacheDir)
        assertTrue(args.containsAll(listOf("--init-script", initScriptFile.absolutePath, "-q")))
    }

    @Test
    fun buildInitScriptArguments_includesProjectCacheDir() {
        val args = buildInitScriptArguments(initScriptFile, projectCacheDir)
        assertTrue(args.containsAll(listOf("--project-cache-dir", projectCacheDir.absolutePath)))
    }

    @Test
    fun buildInitScriptArguments_includesCacheBustSystemProperty() {
        val args = buildInitScriptArguments(initScriptFile, projectCacheDir)
        assertTrue(args.any { it.startsWith("-D$CACHE_BUST_PROPERTY=") })
    }

    @Test
    fun buildInitScriptArguments_cacheBustValueDiffersEachCall() {
        val first =
            buildInitScriptArguments(initScriptFile, projectCacheDir)
                .first { it.startsWith("-D$CACHE_BUST_PROPERTY=") }
        val second =
            buildInitScriptArguments(initScriptFile, projectCacheDir)
                .first { it.startsWith("-D$CACHE_BUST_PROPERTY=") }
        assertNotEquals(first, second)
    }
}
