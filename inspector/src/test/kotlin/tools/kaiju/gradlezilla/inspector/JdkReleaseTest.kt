package tools.kaiju.gradlezilla.inspector

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JdkReleaseTest {
    @Test
    fun `parses a modern quoted JAVA_VERSION`() {
        assertEquals(21, JdkRelease.parseFeatureVersion("\"21.0.1\""))
    }

    @Test
    fun `parses a bare feature version`() {
        assertEquals(17, JdkRelease.parseFeatureVersion("17"))
    }

    @Test
    fun `parses the old 1_x versioning scheme`() {
        assertEquals(8, JdkRelease.parseFeatureVersion("\"1.8.0_392\""))
    }

    @Test
    fun `unparseable version yields null`() {
        assertNull(JdkRelease.parseFeatureVersion("\"not-a-version\""))
    }

    @Test
    fun `reads JAVA_VERSION from a real release file`() {
        val jdkHome =
            File.createTempFile("jdk-release-test", "").apply {
                delete()
                mkdirs()
            }
        File(jdkHome, "release").writeText(
            """
            IMPLEMENTOR="Eclipse Adoptium"
            JAVA_VERSION="17.0.9"
            OS_ARCH="aarch64"
            """.trimIndent(),
        )

        assertEquals(17, JdkRelease.read(jdkHome))
    }

    @Test
    fun `missing release file yields null, never throws`() {
        val emptyDir =
            File.createTempFile("jdk-release-test-empty", "").apply {
                delete()
                mkdirs()
            }
        assertNull(JdkRelease.read(emptyDir))
    }

    @Test
    fun `nonexistent directory yields null, never throws`() {
        assertNull(JdkRelease.read(File("/does/not/exist/anywhere")))
    }
}
