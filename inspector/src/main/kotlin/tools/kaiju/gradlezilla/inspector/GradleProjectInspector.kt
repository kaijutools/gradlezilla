package tools.kaiju.gradlezilla.inspector

import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
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
        val versions = parseVersionCatalog()
        val compileSdk =
            COMPILE_SDK_KEYS.firstNotNullOfOrNull { versions[it]?.toIntOrNull() }
                ?: throw NotAnAndroidProjectException(
                    "Could not determine compileSdk for project at '$projectDir' — " +
                        "no compileSdk entry found in gradle/libs.versions.toml",
                )
        val buildToolsVersion = BUILD_TOOLS_KEYS.firstNotNullOfOrNull { versions[it] }
        val ndkVersion = NDK_KEYS.firstNotNullOfOrNull { versions[it] }
        try {
            GradleConnector
                .newConnector()
                .forProjectDirectory(projectDir)
                .connect()
                .use { connection ->
                    val gradleProject = connection.getModel(GradleProject::class.java)
                    val env = connection.getModel(BuildEnvironment::class.java)
                    return AndroidProjectSpec(
                        jdkVersion = jdkMajorVersion(env.java.javaHome),
                        androidSdkVersion = compileSdk,
                        androidPlatformToolsVersion = buildToolsVersion,
                        androidNdkVersion = ndkVersion,
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

    // ── Version catalog parsing ───────────────────────────────────────────

    private fun parseVersionCatalog(): Map<String, String> {
        val catalog = File(projectDir, "gradle/libs.versions.toml").takeIf { it.exists() } ?: return emptyMap()
        val result = mutableMapOf<String, String>()
        var inVersions = false
        for (line in catalog.readLines()) {
            val trimmed = line.trim()
            when {
                trimmed == "[versions]" -> inVersions = true
                trimmed.startsWith("[") -> inVersions = false
                inVersions && !trimmed.startsWith("#") && trimmed.contains("=") -> {
                    val (rawKey, rawValue) = trimmed.split("=", limit = 2)
                    result[rawKey.trim()] = rawValue.trim().trim('"')
                }
            }
        }
        return result
    }

    // ── Tooling API helpers ───────────────────────────────────────────────

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
        val COMPILE_SDK_KEYS = listOf("compileSdk", "compile-sdk", "compileSdkVersion", "compile_sdk")
        val BUILD_TOOLS_KEYS = listOf("buildTools", "buildToolsVersion", "build-tools", "build_tools")
        val NDK_KEYS = listOf("ndk", "ndkVersion", "ndk-version", "ndk_version", "androidNdk")
    }
}
