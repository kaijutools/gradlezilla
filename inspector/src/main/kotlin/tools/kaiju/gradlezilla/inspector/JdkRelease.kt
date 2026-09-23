package tools.kaiju.gradlezilla.inspector

import java.io.File

/**
 * Reads a JDK's feature version from the `release` file at its home directory — never by
 * shelling out to `java -version`, which would mean launching a JVM per candidate.
 */
object JdkRelease {
    private const val KEY = "JAVA_VERSION="

    /** Null (never throws) if [javaHome] has no readable/parseable `release` file. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun read(javaHome: File): Int? {
        val releaseFile = File(javaHome, "release").takeIf { it.isFile } ?: return null
        return try {
            releaseFile
                .readLines()
                .firstOrNull { it.startsWith(KEY) }
                ?.substring(KEY.length)
                ?.let(::parseFeatureVersion)
        } catch (e: Exception) {
            null
        }
    }

    /** "21.0.1" -> 21, "1.8.0_392" (old scheme) -> 8, "17" -> 17. Null if unparseable. */
    internal fun parseFeatureVersion(raw: String): Int? {
        val cleaned = raw.trim().removeSurrounding("\"")
        val parts = cleaned.split('.', '_', '-').filter { it.isNotEmpty() }
        val first = parts.firstOrNull()?.toIntOrNull() ?: return null
        return if (first == 1 && parts.size > 1) parts[1].toIntOrNull() else first
    }
}
