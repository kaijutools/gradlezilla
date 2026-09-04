package tools.kaiju.gradlezilla.models

import java.io.File
import java.util.*

object JdkPreflight {
    /** Null if all clear. Non-null message means: print and exit before connecting. */
    fun check(projectDir: File): String? {
        val props =
            File(projectDir, "gradle/wrapper/gradle-wrapper.properties")
                .takeIf { it.isFile } ?: return null

        val url =
            props
                .inputStream()
                .use { Properties().apply { load(it) } }
                .getProperty("distributionUrl") ?: return null

        val gradle = GradleVersion.fromDistributionUrl(url) ?: return null
        val running = Runtime.version().feature()

        val problem = GradleJdkCompatibility.check(gradle, running) ?: return null

        return buildString {
            appendLine(
                "${projectDir.name} uses Gradle ${problem.gradleVersion}, " +
                    "which supports JDK ${problem.maxSupportedJdk} or lower.",
            )
            appendLine("You're running JDK ${problem.runningJdk}.")
            appendLine()
            appendLine("Set JAVA_HOME to a compatible JDK and retry:")
            appendLine()
            appendLine("  $suggestion")
        }
    }

    private val suggestion: String
        get() =
            if (System.getProperty("os.name").startsWith("Mac")) {
                "JAVA_HOME=\$(/usr/libexec/java_home -v 17) gradlezilla generate ."
            } else {
                "JAVA_HOME=/path/to/jdk17 gradlezilla generate ."
            }
}
