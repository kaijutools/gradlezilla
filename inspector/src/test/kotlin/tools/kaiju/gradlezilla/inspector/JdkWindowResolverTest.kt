package tools.kaiju.gradlezilla.inspector

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JdkWindowResolverTest {
    private val tempDirs = mutableListOf<File>()

    private fun projectDir(): File {
        val dir =
            File.createTempFile("jdk-window-test", "").apply {
                delete()
                mkdirs()
            }
        tempDirs += dir
        return dir
    }

    @AfterTest
    fun cleanup() {
        tempDirs.forEach { it.deleteRecursively() }
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

    private fun writeVersionCatalogAgp(
        projectDir: File,
        agpVersion: String,
    ) {
        val gradleDir = File(projectDir, "gradle").apply { mkdirs() }
        File(gradleDir, "libs.versions.toml").writeText(
            """
            [versions]
            agp = "$agpVersion"
            """.trimIndent(),
        )
    }

    @Test
    fun `no wrapper file leaves both bounds open`() {
        val window = JdkWindowResolver.resolve(projectDir())
        assertNull(window.floor)
        assertNull(window.ceiling)
    }

    @Test
    fun `ceiling comes from the Gradle wrapper version`() {
        val dir = projectDir()
        writeWrapper(dir, "gradle-8.1-bin.zip")

        assertEquals(19, JdkWindowResolver.resolve(dir).ceiling)
    }

    @Test
    fun `floor comes from the AGP version in the version catalog`() {
        val dir = projectDir()
        writeWrapper(dir, "gradle-8.1-bin.zip")
        writeVersionCatalogAgp(dir, "8.5.0")

        assertEquals(17, JdkWindowResolver.resolve(dir).floor)
    }

    @Test
    fun `sunflower-shaped project resolves to the 17-19 window`() {
        val dir = projectDir()
        writeWrapper(dir, "gradle-8.1-bin.zip")
        writeVersionCatalogAgp(dir, "8.2.0")

        val window = JdkWindowResolver.resolve(dir)
        assertEquals(17, window.floor)
        assertEquals(19, window.ceiling)
    }
}
