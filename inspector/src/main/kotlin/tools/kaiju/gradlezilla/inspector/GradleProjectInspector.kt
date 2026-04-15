package tools.kaiju.gradlezilla.inspector

import com.android.builder.model.AndroidProject
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.UnknownModelException
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import tools.kaiju.gradlezilla.models.AndroidProjectSpec
import tools.kaiju.gradlezilla.models.ModuleSpec
import java.io.File

class GradleProjectInspector(
    private val projectDir: File,
) {
    fun targets(): List<BuildTarget> {
        validateGradleProject()
        try {
            GradleConnector
                .newConnector()
                .forProjectDirectory(projectDir)
                .connect()
                .use { connection ->
                    val project = connection.getModel(GradleProject::class.java)
                    return project.tasks
                        .map { task ->
                            BuildTarget(
                                name = task.name,
                                path = task.path,
                                group = task.group?.takeIf { it.isNotBlank() },
                                description = task.description?.takeIf { it.isNotBlank() },
                            )
                        }.sortedWith(compareBy({ it.group ?: "\uFFFF" }, { it.path }))
                }
        } catch (e: GradleConnectionException) {
            throw GradleInspectorException("Could not connect to Gradle project at '$projectDir': ${e.message}", e)
        }
    }

    fun inspect(): AndroidProjectSpec {
        validateGradleProject()
        try {
            GradleConnector
                .newConnector()
                .forProjectDirectory(projectDir)
                .connect()
                .use { connection ->
                    val gradleProject = connection.getModel(GradleProject::class.java)
                    val env = connection.getModel(BuildEnvironment::class.java)
                    val (androidProject, compileSdk) = resolveAndroidModel(connection)

                    return AndroidProjectSpec(
                        jdkVersion = jdkMajorVersion(env.java.javaHome),
                        androidSdkVersion = compileSdk,
                        androidPlatformToolsVersion = androidProject.buildToolsVersion,
                        // ndkVersion not surfaced by builder-model API; use a BuildAction to extend
                        androidNdkVersion = null,
                        gradleVersion = env.gradle.gradleVersion,
                        gradleJvmArgs = env.java.jvmArguments.joinToString(" ").takeIf { it.isNotBlank() },
                        modules = collectModules(gradleProject),
                        hasBuildSrc = File(projectDir, "buildSrc").isDirectory,
                        hasBuildLogic = File(projectDir, "build-logic").isDirectory,
                    )
                }
        } catch (e: GradleConnectionException) {
            throw GradleInspectorException("Could not connect to Gradle project at '$projectDir': ${e.message}", e)
        }
    }

    private fun resolveAndroidModel(connection: org.gradle.tooling.ProjectConnection): Pair<AndroidProject, Int> {
        val androidProject =
            try {
                connection.getModel(AndroidProject::class.java)
            } catch (e: UnknownModelException) {
                throw NotAnAndroidProjectException(
                    "Project at '$projectDir' does not apply the Android Gradle Plugin",
                    e,
                )
            }
        val compileSdk =
            androidProject.compileTarget
                .removePrefix("android-")
                .toIntOrNull()
                ?: throw GradleInspectorException(
                    "Unrecognised compileTarget '${androidProject.compileTarget}'",
                )
        return androidProject to compileSdk
    }

    private fun validateGradleProject() {
        val hasSettingsFile = SETTINGS_FILES.any { File(projectDir, it).exists() }
        if (!hasSettingsFile) {
            throw NotAGradleProjectException(
                "No Gradle settings file found in '$projectDir' — expected one of: ${SETTINGS_FILES.joinToString()}",
            )
        }
    }

    private fun collectModules(project: GradleProject): List<ModuleSpec> =
        project.children.flatMap { child ->
            listOf(ModuleSpec(path = child.path, isApplication = child.tasks.any { it.name == "installDebug" })) +
                collectModules(child)
        }

    private fun jdkMajorVersion(javaHome: File): Int {
        val version =
            File(javaHome, "release")
                .takeIf { it.exists() }
                ?.readLines()
                ?.firstOrNull { it.startsWith("JAVA_VERSION=") }
                ?.removePrefix("JAVA_VERSION=")
                ?.trim('"')
                ?: return DEFAULT_JDK_VERSION
        val parts = version.split(".")
        return if (parts[0] == "1") parts[1].toInt() else parts[0].toInt()
    }

    private companion object {
        const val DEFAULT_JDK_VERSION = 17
        val SETTINGS_FILES = listOf("settings.gradle.kts", "settings.gradle")
    }
}
