package tools.kaiju.gradlezilla.models

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DaemonJvmCriteriaTest {
    @Test
    fun `returns null when gradle-daemon-jvm properties does not exist`() {
        val projectDir = createTempDir()
        assertNull(DaemonJvmCriteria.read(projectDir))
    }

    @Test
    fun `reads the toolchainVersion key when present`() {
        val projectDir = createTempDir()
        writeDaemonJvmProperties(projectDir, "toolchainVersion=21\n")

        assertEquals(21, DaemonJvmCriteria.read(projectDir))
    }

    @Test
    fun `returns null when toolchainVersion is missing`() {
        val projectDir = createTempDir()
        writeDaemonJvmProperties(projectDir, "toolchainVendor=ADOPTIUM\n")

        assertNull(DaemonJvmCriteria.read(projectDir))
    }

    @Test
    fun `returns null when toolchainVersion is unparseable`() {
        val projectDir = createTempDir()
        writeDaemonJvmProperties(projectDir, "toolchainVersion=not-a-number\n")

        assertNull(DaemonJvmCriteria.read(projectDir))
    }

    private fun createTempDir(): File =
        File.createTempFile("daemon-jvm-criteria-test", "").apply {
            delete()
            mkdirs()
        }

    private fun writeDaemonJvmProperties(
        projectDir: File,
        contents: String,
    ) {
        val gradleDir = File(projectDir, "gradle").apply { mkdirs() }
        File(gradleDir, "gradle-daemon-jvm.properties").writeText(contents)
    }
}
