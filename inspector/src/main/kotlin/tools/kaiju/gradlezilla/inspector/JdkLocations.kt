package tools.kaiju.gradlezilla.inspector

import tools.kaiju.gradlezilla.models.JdkSource
import java.io.File

/** OS-specific well-known JDK install locations. Never throws — an unreadable path is skipped. */
interface JdkLocations {
    val source: JdkSource

    fun discover(): List<File>

    companion object {
        fun forCurrentOs(osName: String = System.getProperty("os.name").orEmpty()): JdkLocations =
            when {
                osName.startsWith("Mac", ignoreCase = true) -> MacJdkLocations()
                osName.contains("Linux", ignoreCase = true) -> LinuxJdkLocations()
                osName.startsWith("Windows", ignoreCase = true) -> WindowsJdkLocations()
                // BSD and anything else: no known well-known layout, best-effort skip.
                else -> NoOpJdkLocations
            }
    }
}

/** [dir]'s immediate subdirectories, or empty if it doesn't exist / isn't readable. Never throws. */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal fun File.childDirs(): List<File> =
    try {
        listFiles()?.filter { it.isDirectory }.orEmpty()
    } catch (e: Exception) {
        emptyList()
    }

class MacJdkLocations(
    private val runJavaHomeV: () -> String? = ::invokeJavaHomeV,
) : JdkLocations {
    override val source = JdkSource.MACOS_JAVA_HOME

    override fun discover(): List<File> = runJavaHomeV()?.let(::parse).orEmpty()

    internal companion object {
        // Matching entries are indented, e.g.:
        //     21.0.1 (arm64) "Eclipse Adoptium" - "OpenJDK 21.0.1" /Library/.../Contents/Home
        // The final unindented line (the selected default) is intentionally excluded — every
        // installed JVM is a candidate, not just whichever one java_home currently prefers.
        private val PATH_RE = Regex("""(/\S+)\s*$""")

        internal fun parse(output: String): List<File> =
            output
                .lineSequence()
                .filter { it.isNotEmpty() && it.first().isWhitespace() }
                .mapNotNull { PATH_RE.find(it)?.groupValues?.get(1) }
                .map(::File)
                .toList()
    }
}

@Suppress("TooGenericExceptionCaught", "SwallowedException")
private fun invokeJavaHomeV(): String? =
    try {
        val process =
            ProcessBuilder("/usr/libexec/java_home", "-V")
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        output
    } catch (e: Exception) {
        null
    }

class LinuxJdkLocations : JdkLocations {
    override val source = JdkSource.SYSTEM_PATH

    override fun discover(): List<File> =
        listOf("/usr/lib/jvm", "/usr/java", "/opt/java").flatMap { File(it).childDirs() } +
            File("/opt").childDirs().filter { it.name.startsWith("jdk") }
}

class WindowsJdkLocations : JdkLocations {
    override val source = JdkSource.SYSTEM_PATH

    override fun discover(): List<File> {
        val roots =
            buildList {
                add("""C:\Program Files\Java""")
                add("""C:\Program Files\Eclipse Adoptium""")
                System.getenv("LOCALAPPDATA")?.let { add("$it\\Programs") }
            }
        return roots
            .flatMap { File(it).childDirs() }
            .filter { File(it, "bin/java.exe").isFile }
    }
}

object NoOpJdkLocations : JdkLocations {
    override val source = JdkSource.SYSTEM_PATH

    override fun discover(): List<File> = emptyList()
}
