package tools.kaiju.gradlezilla.models

import java.io.File

/**
 * Where a candidate JDK for the extraction daemon was found. Distinct from [JdkVersionSource],
 * which is about spec.jdkVersion — a project-derived fact, never the daemon's own JVM.
 */
enum class JdkSource {
    FLAG,
    JAVA_HOME,
    MACOS_JAVA_HOME,
    GRADLE_JDKS,
    SDKMAN,
    ASDF,
    SYSTEM_PATH,
    CURRENT_JVM,
}

fun JdkSource.wireName(): String =
    when (this) {
        JdkSource.FLAG -> "flag"
        JdkSource.JAVA_HOME -> "JAVA_HOME"
        JdkSource.MACOS_JAVA_HOME -> "java_home"
        JdkSource.GRADLE_JDKS -> "gradleJdks"
        JdkSource.SDKMAN -> "sdkman"
        JdkSource.ASDF -> "asdf"
        JdkSource.SYSTEM_PATH -> "systemPath"
        JdkSource.CURRENT_JVM -> "currentJvm"
    }

data class JdkCandidate(
    val javaHome: File,
    val version: Int,
    val source: JdkSource,
)

/** Inclusive JDK version range the daemon must run in. Either bound absent means unconstrained. */
data class JdkWindow(
    val floor: Int?,
    val ceiling: Int?,
) {
    operator fun contains(version: Int): Boolean {
        val aboveFloor = floor == null || version >= floor
        val belowCeiling = ceiling == null || version <= ceiling
        return aboveFloor && belowCeiling
    }
}

sealed class JdkSelectionResult {
    data class Selected(
        val javaHome: File,
        val version: Int,
        val source: JdkSource,
    ) : JdkSelectionResult()

    data class NoCompatibleJdk(
        val candidatesConsidered: List<JdkCandidate>,
        val requiredRange: JdkWindow,
    ) : JdkSelectionResult()
}

/**
 * Picks a JDK for the Gradle Tooling API connection from whatever was discovered on the
 * machine — never from the JVM running the CLI itself. Pure and deterministic for a given
 * candidate set: e2e/determinism.sh re-runs against the same machine state and expects the
 * same pick every time.
 */
object JdkSelector {
    // Known LTS releases, highest first — preferred over any non-LTS version inside the window.
    @Suppress("MagicNumber")
    private val LTS_VERSIONS = listOf(25, 21, 17, 11, 8)

    // Fixed tie-break order for candidates that land on the same version from different
    // sources, so the pick never depends on filesystem/glob iteration order.
    private val SOURCE_PRIORITY =
        listOf(
            JdkSource.JAVA_HOME,
            JdkSource.CURRENT_JVM,
            JdkSource.GRADLE_JDKS,
            JdkSource.SDKMAN,
            JdkSource.ASDF,
            JdkSource.MACOS_JAVA_HOME,
            JdkSource.SYSTEM_PATH,
            JdkSource.FLAG,
        )

    fun select(
        candidates: List<JdkCandidate>,
        window: JdkWindow,
    ): JdkSelectionResult {
        val javaHomeCandidate = candidates.firstOrNull { it.source == JdkSource.JAVA_HOME }
        if (javaHomeCandidate != null && javaHomeCandidate.version in window) {
            return JdkSelectionResult.Selected(
                javaHomeCandidate.javaHome,
                javaHomeCandidate.version,
                javaHomeCandidate.source,
            )
        }

        val inWindow = candidates.filter { it.version in window }
        val best =
            inWindow
                .sortedWith(
                    compareByDescending<JdkCandidate> { LTS_VERSIONS.contains(it.version) }
                        .thenByDescending { it.version }
                        .thenBy { SOURCE_PRIORITY.indexOf(it.source).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE },
                ).firstOrNull()

        return if (best != null) {
            JdkSelectionResult.Selected(best.javaHome, best.version, best.source)
        } else {
            JdkSelectionResult.NoCompatibleJdk(candidates, window)
        }
    }
}
