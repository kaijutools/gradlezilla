package tools.kaiju.gradlezilla.inspector

import tools.kaiju.gradlezilla.models.AgpJdkCompatibility
import tools.kaiju.gradlezilla.models.DaemonJvmCriteria
import tools.kaiju.gradlezilla.models.GradleJdkCompatibility
import tools.kaiju.gradlezilla.models.GradleVersion
import tools.kaiju.gradlezilla.models.JdkWindow
import java.io.File
import java.util.Properties

/**
 * The daemon JDK's compatible window, computed entirely from files on disk — before any
 * Gradle connection exists, since we need a JDK to connect with in the first place.
 *
 * Ceiling: the max JDK the project's own Gradle wrapper supports (Gradle refuses above it).
 * Floor: AGP's minimum JDK (best-effort, from the version catalog) and/or a declared daemon
 * JVM criteria — both optional, so either or both bounds may be absent.
 */
object JdkWindowResolver {
    fun resolve(projectDir: File): JdkWindow {
        val ceiling = gradleVersion(projectDir)?.let(GradleJdkCompatibility::maxSupportedJdk)

        val agpFloor = AgpVersionHint.fromVersionCatalog(projectDir)?.let(AgpJdkCompatibility::minimumJdk)
        val daemonJvmCriteria = DaemonJvmCriteria.read(projectDir)
        val floor = maxOf(agpFloor ?: 0, daemonJvmCriteria ?: 0).takeIf { it > 0 }

        return JdkWindow(floor, ceiling)
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun gradleVersion(projectDir: File): GradleVersion? {
        val props = File(projectDir, "gradle/wrapper/gradle-wrapper.properties").takeIf { it.isFile } ?: return null
        return try {
            props
                .inputStream()
                .use { Properties().apply { load(it) } }
                .getProperty("distributionUrl")
                ?.let(GradleVersion::fromDistributionUrl)
        } catch (e: Exception) {
            null
        }
    }
}
