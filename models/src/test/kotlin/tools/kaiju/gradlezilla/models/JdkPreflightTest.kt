package tools.kaiju.gradlezilla.models

import java.io.File
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JdkPreflightTest {
    @Test
    fun `passes through when there is no wrapper file`() {
        val projectDir = createTempDir()
        assertNull(JdkPreflight.check(projectDir))
    }

    @Test
    fun `suggests the actual max supported JDK, not a hardcoded one`() {
        val projectDir = createTempDir()
        writeWrapper(projectDir, "gradle-5.0-bin.zip")

        val message = JdkPreflight.check(projectDir)

        assertTrue(message != null, "expected a preflight failure for an ancient Gradle version")
        assertTrue(
            message.contains("11"),
            "expected the suggested JDK (11, per GradleJdkCompatibility for Gradle 5.0) in: $message",
        )
    }

    private fun createTempDir(): File =
        File.createTempFile("jdk-preflight-test", "").apply {
            delete()
            mkdirs()
        }

    private fun writeWrapper(
        projectDir: File,
        distributionZip: String,
    ) {
        val wrapperDir = File(projectDir, "gradle/wrapper").apply { mkdirs() }
        File(wrapperDir, "gradle-wrapper.properties").writeText(
            "distributionUrl=https\\://services.gradle.org/distributions/$distributionZip\n",
        )
    }
}
