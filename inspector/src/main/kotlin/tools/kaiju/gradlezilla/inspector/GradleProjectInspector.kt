package tools.kaiju.gradlezilla.inspector

import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import tools.kaiju.gradlezilla.inspector.initscript.InitScriptExtractor
import tools.kaiju.gradlezilla.inspector.versioncatalog.VersionCatalogExtractor
import tools.kaiju.gradlezilla.models.*
import java.io.File

class GradleProjectInspector(
    private val projectDir: File,
) {
    private val extractors: List<AgpDataExtractor> =
        listOf(
            InitScriptExtractor(),
            VersionCatalogExtractor(),
            StaticBuildFileExtractor(),
        )

    @Throws(GradleInspectorException::class)
    fun targets(): List<BuildTarget> {
        validateGradleProject()
        JdkPreflight.check(projectDir)?.let { throw GradleInspectorException(it) }

        return try {
            connect().use { connection ->
                val project = connection.getModel(GradleProject::class.java)
                project.tasks
                    .map { task ->
                        BuildTarget(
                            name = task.name,
                            path = task.path,
                            group = task.group?.takeIf { it.isNotBlank() },
                            description = task.description?.takeIf { it.isNotBlank() },
                        )
                    }.sortedWith(compareBy({ it.group ?: "\uFFFF" }, { it.path }))
            }
        } catch (e: GradleInspectorException) {
            throw e
        }
    }

    @Throws(GradleInspectorException::class)
    fun inspect(): AndroidProjectSpec {
        validateGradleProject()
        JdkPreflight.check(projectDir)?.let { throw GradleInspectorException(it) }

        return try {
            connect().use { connection ->
                val env = fetchEnvironment(connection)
                val ctx = ExtractionContext(projectDir, connection, env)
                val agpData = executeExtractionChain(ctx)
                return AndroidProjectSpec(
                    jdkVersion = env.jdkVersion,
                    gradleVersion = env.gradleVersion,
                    androidSdkVersion = agpData.compileSdk,
                    androidPlatformToolsVersion = agpData.buildToolsVersion,
                    androidNdkVersion = agpData.ndkVersion,
                )
            }
        } catch (e: GradleInspectorException) {
            throw e
        }
    }

    @Throws(GradleInspectorException::class)
    internal fun connect(): ProjectConnection =
        try {
            GradleConnector
                .newConnector()
                .forProjectDirectory(projectDir)
                .connect()
        } catch (e: GradleConnectionException) {
            throw GradleInspectorException(
                "Could not connect to Gradle project at '$projectDir': ${e::class.simpleName}::${e.message}",
                e,
            )
        }

    @Throws(GradleInspectorException::class)
    internal fun fetchEnvironment(connection: ProjectConnection): GradleProjectEnvironment {
        val hasBuildSrc = File(projectDir, "buildSrc").isDirectory
        val hasBuildLogic = File(projectDir, "build-logic").isDirectory

        try {
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
        } catch (e: GradleConnectionException) {
            throw GradleInspectorException("Could not connect to $projectDir: ${e.message}", e)
        }
    }

    @Throws(GradleInspectorException::class)
    internal fun executeExtractionChain(context: ExtractionContext): AgpData {
        val attempts = mutableListOf<Pair<String, ExtractionOutcome>>()
        for (extractor in extractors) {
            when (val outcome = extractor.extract(context)) {
                is ExtractionOutcome.Found -> return outcome.data
                else -> attempts += extractor.name to outcome
            }
        }

        throw GradleInspectorException(
            buildString {
                appendLine("Could not extract Android configuration from ${context.projectDir}")
                attempts.forEach { (name, outcome) ->
                    val reason =
                        when (outcome) {
                            is ExtractionOutcome.NotApplicable -> outcome.reason
                            is ExtractionOutcome.Failed -> outcome.reason
                            is ExtractionOutcome.Found -> error("unreachable")
                        }
                    appendLine("    $name: $reason")
                }
            },
        )
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
