package tools.kaiju.gradlezilla.inspector

import tools.kaiju.gradlezilla.models.JdkSelectionResult
import tools.kaiju.gradlezilla.models.JdkSelector
import tools.kaiju.gradlezilla.models.JdkSource
import tools.kaiju.gradlezilla.models.JdkWindow
import tools.kaiju.gradlezilla.models.wireName
import java.io.File

/**
 * Picks the JDK the extraction daemon connects with — an implementation detail of our Tooling
 * API connection, entirely separate from spec.jdkVersion (which [tools.kaiju.gradlezilla.models
 * .JdkResolver] derives from the project itself and never from whatever JVM runs the daemon).
 */
object DaemonJdk {
    @Suppress("MagicNumber")
    private val LTS_VERSIONS = listOf(25, 21, 17, 11, 8)

    @Throws(GradleInspectorException::class)
    fun resolve(
        projectDir: File,
        override: File?,
    ): JdkSelectionResult.Selected {
        val window = JdkWindowResolver.resolve(projectDir)

        if (override != null) return resolveOverride(projectDir, override, window)

        return when (val result = JdkSelector.select(JdkDiscovery.discover(), window)) {
            is JdkSelectionResult.Selected -> result
            is JdkSelectionResult.NoCompatibleJdk ->
                throw GradleInspectorException(noCompatibleJdkMessage(projectDir, result))
        }
    }

    private fun resolveOverride(
        projectDir: File,
        override: File,
        window: JdkWindow,
    ): JdkSelectionResult.Selected {
        val version =
            JdkRelease.read(override)
                ?: throw GradleInspectorException("--daemon-jdk '$override' has no readable JDK release file")
        if (version !in window) {
            throw GradleInspectorException(
                "--daemon-jdk '$override' is JDK $version, but ${projectDir.name} requires " +
                    window.describe(),
            )
        }
        return JdkSelectionResult.Selected(override, version, JdkSource.FLAG)
    }

    private fun noCompatibleJdkMessage(
        projectDir: File,
        result: JdkSelectionResult.NoCompatibleJdk,
    ): String =
        buildString {
            appendLine("${projectDir.name} needs ${result.requiredRange.describe()} to run its Gradle daemon.")
            appendLine()
            if (result.candidatesConsidered.isEmpty()) {
                appendLine("No JDKs were found on this machine.")
            } else {
                appendLine("Found on this machine:")
                result.candidatesConsidered
                    .sortedByDescending { it.version }
                    .forEach { appendLine("  - JDK ${it.version} (${it.source.wireName()}) at ${it.javaHome}") }
            }
            appendLine()
            appendLine("Install one, e.g.:")
            appendLine()
            appendLine("  ${installSuggestion(suggestedVersion(result.requiredRange))}")
        }

    private fun suggestedVersion(window: JdkWindow): Int =
        LTS_VERSIONS.firstOrNull { it in window }
            ?: window.ceiling
            ?: window.floor
            ?: LTS_VERSIONS.last()

    private fun installSuggestion(version: Int): String {
        val osName = System.getProperty("os.name").orEmpty()
        return when {
            osName.startsWith("Mac", ignoreCase = true) -> "brew install --cask temurin@$version"
            osName.startsWith("Windows", ignoreCase = true) -> "winget install EclipseAdoptium.Temurin.$version.JDK"
            else -> "sdk install java $version-tem"
        }
    }
}

private fun JdkWindow.describe(): String =
    when {
        floor != null && ceiling != null -> "a JDK between $floor and $ceiling"
        floor != null -> "JDK $floor or newer"
        ceiling != null -> "JDK $ceiling or older"
        else -> "a JDK"
    }
