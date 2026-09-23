package tools.kaiju.gradlezilla.inspector

import tools.kaiju.gradlezilla.models.JdkSource
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeJdkLocations(
    override val source: JdkSource,
    private val homes: List<File>,
) : JdkLocations {
    override fun discover(): List<File> = homes
}

class JdkDiscoveryTest {
    private val tempDirs = mutableListOf<File>()

    private fun jdkHome(version: String): File {
        val dir =
            File.createTempFile("jdk-discovery-test", "").apply {
                delete()
                mkdirs()
            }
        File(dir, "release").writeText("JAVA_VERSION=\"$version\"\n")
        tempDirs += dir
        return dir
    }

    @AfterTest
    fun cleanup() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    @Test
    fun `combines portable sources with OS-specific locations`() {
        val currentJvm = jdkHome("21")
        val osJdk = jdkHome("17")
        val emptyHome =
            File.createTempFile("jdk-discovery-home", "").apply {
                delete()
                mkdirs()
            }
        tempDirs += emptyHome

        val candidates =
            JdkDiscovery.discover(
                osLocations = FakeJdkLocations(JdkSource.SYSTEM_PATH, listOf(osJdk)),
                env = emptyMap(),
                userHome = emptyHome,
                currentJvmHome = currentJvm,
            )

        val versionsAndSources = candidates.map { it.version to it.source }.toSet()
        assertEquals(setOf(21 to JdkSource.CURRENT_JVM, 17 to JdkSource.SYSTEM_PATH), versionsAndSources)
    }

    @Test
    fun `dedupes the same canonical path found via two sources`() {
        val shared = jdkHome("17")
        val emptyHome =
            File.createTempFile("jdk-discovery-home", "").apply {
                delete()
                mkdirs()
            }
        tempDirs += emptyHome

        val candidates =
            JdkDiscovery.discover(
                osLocations = FakeJdkLocations(JdkSource.SYSTEM_PATH, listOf(shared)),
                env = mapOf("JAVA_HOME" to shared.absolutePath),
                userHome = emptyHome,
                currentJvmHome = shared,
            )

        assertEquals(1, candidates.size)
        assertEquals(JdkSource.JAVA_HOME, candidates.single().source)
    }

    @Test
    fun `unreadable candidates are skipped, never thrown`() {
        val emptyHome =
            File.createTempFile("jdk-discovery-home", "").apply {
                delete()
                mkdirs()
            }
        tempDirs += emptyHome
        val notAJdk =
            File.createTempFile("not-a-jdk", "").apply {
                delete()
                mkdirs()
            }
        tempDirs += notAJdk

        val candidates =
            JdkDiscovery.discover(
                osLocations = FakeJdkLocations(JdkSource.SYSTEM_PATH, listOf(notAJdk, File("/does/not/exist"))),
                env = emptyMap(),
                userHome = emptyHome,
                currentJvmHome = File("/also/does/not/exist"),
            )

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `reads Gradle-provisioned toolchains under ~-gradle-jdks`() {
        val home =
            File.createTempFile("jdk-discovery-home", "").apply {
                delete()
                mkdirs()
            }
        tempDirs += home
        val gradleJdk = File(home, ".gradle/jdks/temurin-17").apply { mkdirs() }
        File(gradleJdk, "release").writeText("JAVA_VERSION=\"17.0.9\"\n")

        val candidates =
            JdkDiscovery.discover(
                osLocations = FakeJdkLocations(JdkSource.SYSTEM_PATH, emptyList()),
                env = emptyMap(),
                userHome = home,
                currentJvmHome = File("/does/not/exist"),
            )

        assertEquals(listOf(17 to JdkSource.GRADLE_JDKS), candidates.map { it.version to it.source })
    }
}
