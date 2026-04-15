package tools.kaiju.gradlezilla.inspector

import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import tools.kaiju.gradlezilla.inspector.versioncatalog.VersionCatalogExtractor
import tools.kaiju.gradlezilla.models.*
import java.io.File

class GradleProjectInspector(
    private val projectDir: File,
) {
    private val extractors: List<AgpDataExtractor> =
        listOf(
            VersionCatalogExtractor(),
            StaticBuildFileExtractor(),
            InitScriptExtractor(),
        )

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

        val env = fetchEnvironment()

        val agpData = executeExtractionChain()

        return AndroidProjectSpec(
            jdkVersion = env.jdkVersion,
            gradleVersion = env.gradleVersion,
            androidSdkVersion = agpData.compileSdk,
            androidPlatformToolsVersion = agpData.buildToolsVersion,
            androidNdkVersion = agpData.ndkVersion,
        )
    }

    @Throws(GradleInspectorException::class)
    internal fun fetchEnvironment(): GradleProjectEnvironment {
        val hasBuildSrc = File(projectDir, "buildSrc").isDirectory
        val hasBuildLogic = File(projectDir, "build-logic").isDirectory

        try {
            GradleConnector
                .newConnector()
                .forProjectDirectory(projectDir)
                .connect()
                .use { connection ->
                    val buildEnv = connection.getModel(BuildEnvironment::class.java)

                    val gradleProject = connection.getModel(GradleProject::class.java)

                    return GradleProjectEnvironment(
                        jdkVersion = jdkMajorVersion(buildEnv.java.javaHome),
                        gradleVersion = buildEnv.gradle.gradleVersion,
                        gradleJvmArgs =
                            buildEnv.java.jvmArguments
                                .joinToString(" ")
                                .takeIf { it.isNotBlank() },
                        modules = collectModules(gradleProject),
                        hasBuildSrc = hasBuildSrc,
                        hasBuildLogic = hasBuildLogic,
                    )
                }
        } catch (e: GradleConnectionException) {
            throw GradleInspectorException("Could not connect to $projectDir: ${e.message}", e)
        }
    }

    @Throws(GradleInspectorException::class)
    internal fun executeExtractionChain(): AgpData {
        for (extractor in extractors) {
            try {
                val result = extractor.extract(projectDir)
                if (result != null) {
                    return result
                }
            } catch (e: AgpDataExtractionException) {
                println("Failed to extract $projectDir: ${e.message}")
            }
        }

        throw GradleInspectorException("Could not extract Android projects from $projectDir")
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

    private fun validateGradleProject() {
        val hasSettingsFile = SETTINGS_FILES.any { File(projectDir, it).exists() }
        if (!hasSettingsFile) {
            throw NotAGradleProjectException(
                "No Gradle settings file found in '$projectDir' — expected one of: ${SETTINGS_FILES.joinToString()}",
            )
        }
    }

    private companion object {
        const val DEFAULT_JDK_VERSION = 17
        val SETTINGS_FILES = listOf("settings.gradle.kts", "settings.gradle")
    }
}
