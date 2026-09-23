package tools.kaiju.gradlezilla.inspector

import tools.kaiju.gradlezilla.models.JdkCandidate
import tools.kaiju.gradlezilla.models.JdkSource
import java.io.File

/**
 * Finds every JDK plausibly installed on this machine — portable sources common to every OS,
 * plus [osLocations] for OS-specific well-known directories. Never throws: an unreadable
 * candidate is silently skipped, never a fatal error.
 */
object JdkDiscovery {
    fun discover(
        osLocations: JdkLocations = JdkLocations.forCurrentOs(),
        env: Map<String, String> = System.getenv(),
        userHome: File = File(System.getProperty("user.home")),
        currentJvmHome: File = File(System.getProperty("java.home")),
    ): List<JdkCandidate> {
        val raw = mutableListOf<Pair<File, JdkSource>>()

        env["JAVA_HOME"]?.takeIf { it.isNotBlank() }?.let { raw += File(it) to JdkSource.JAVA_HOME }
        raw += currentJvmHome to JdkSource.CURRENT_JVM
        raw += File(userHome, ".gradle/jdks").childDirs().map { it to JdkSource.GRADLE_JDKS }
        raw += File(userHome, ".sdkman/candidates/java").childDirs().map { it to JdkSource.SDKMAN }
        raw += File(userHome, ".asdf/installs/java").childDirs().map { it to JdkSource.ASDF }
        raw +=
            listOf(".local/share/mise/installs/java", ".mise/installs/java")
                .flatMap { File(userHome, it).childDirs() }
                .map { it to JdkSource.SYSTEM_PATH }
        raw += osLocations.discover().map { it to osLocations.source }

        val seenCanonicalPaths = mutableSetOf<String>()
        return raw.mapNotNull { (dir, source) ->
            val version = JdkRelease.read(dir) ?: return@mapNotNull null
            val canonicalPath = dir.runCatchingCanonicalPath() ?: return@mapNotNull null
            if (!seenCanonicalPaths.add(canonicalPath)) return@mapNotNull null
            JdkCandidate(dir, version, source)
        }
    }
}

@Suppress("TooGenericExceptionCaught", "SwallowedException")
private fun File.runCatchingCanonicalPath(): String? =
    try {
        canonicalPath
    } catch (e: Exception) {
        null
    }
