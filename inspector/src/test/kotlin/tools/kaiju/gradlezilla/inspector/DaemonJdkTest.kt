package tools.kaiju.gradlezilla.inspector

import tools.kaiju.gradlezilla.models.JdkSource
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DaemonJdkTest {
    private val tempDirs = mutableListOf<File>()

    private fun projectDir(distributionZip: String): File {
        val dir =
            File.createTempFile("daemon-jdk-test", "").apply {
                delete()
                mkdirs()
            }
        tempDirs += dir
        val wrapperDir = File(dir, "gradle/wrapper").apply { mkdirs() }
        File(wrapperDir, "gradle-wrapper.properties").writeText(
            "distributionUrl=https\\://services.gradle.org/distributions/$distributionZip\n",
        )
        return dir
    }

    private fun jdkHome(version: String): File {
        val dir =
            File.createTempFile("daemon-jdk-home-test", "").apply {
                delete()
                mkdirs()
            }
        tempDirs += dir
        File(dir, "release").writeText("JAVA_VERSION=\"$version\"\n")
        return dir
    }

    @AfterTest
    fun cleanup() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    @Test
    fun `compatible daemon-jdk override is used as-is with source FLAG`() {
        val project = projectDir("gradle-8.1-bin.zip")
        val override = jdkHome("17.0.1")

        val selected = DaemonJdk.resolve(project, override)

        assertEquals(override, selected.javaHome)
        assertEquals(17, selected.version)
        assertEquals(JdkSource.FLAG, selected.source)
    }

    @Test
    fun `incompatible daemon-jdk override fails loudly`() {
        val project = projectDir("gradle-8.1-bin.zip")
        val override = jdkHome("21")

        val exception =
            try {
                DaemonJdk.resolve(project, override)
                null
            } catch (e: GradleInspectorException) {
                e
            }

        assertTrue(exception != null, "expected an incompatible --daemon-jdk to fail loudly")
        assertTrue(exception!!.message!!.contains("--daemon-jdk"))
        assertFalse(exception.message!!.contains("Found on this machine"), "override failure shouldn't run discovery")
    }

    @Test
    fun `unreadable daemon-jdk override fails loudly`() {
        val project = projectDir("gradle-8.1-bin.zip")
        val notAJdk =
            File.createTempFile("not-a-jdk", "").apply {
                delete()
                mkdirs()
            }
        tempDirs += notAJdk

        val exception =
            try {
                DaemonJdk.resolve(project, notAJdk)
                null
            } catch (e: GradleInspectorException) {
                e
            }

        assertTrue(exception != null, "expected an unreadable --daemon-jdk to fail loudly")
    }
}
