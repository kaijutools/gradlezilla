package tools.kaiju.gradlezilla.cli

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class GenerateTest {
    @Test
    fun resolveOutputPath_dryRun_returnsNull() {
        assertNull(resolveOutputPath(File("."), "dockerfile contents", dryRun = true))
    }

    @Test
    fun resolveOutputPath_relativeProjectDir_hasNoDotSegment() {
        val projectDir = createTempDir()
        val relativeProjectDir = File(projectDir, ".")

        val outputPath = resolveOutputPath(relativeProjectDir, "dockerfile contents", dryRun = false)

        assertFalse(outputPath!!.contains("/./"))
        assertEquals(File(projectDir, "Dockerfile").canonicalFile.absolutePath, outputPath)
    }

    private fun createTempDir(): File =
        File.createTempFile("generate-test", "").apply {
            delete()
            mkdirs()
        }
}
