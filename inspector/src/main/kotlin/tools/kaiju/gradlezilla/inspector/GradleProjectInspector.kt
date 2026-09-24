package tools.kaiju.gradlezilla.inspector

import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import tools.kaiju.gradlezilla.inspector.initscript.InitScriptExtractor
import tools.kaiju.gradlezilla.inspector.versioncatalog.VersionCatalogExtractor
import tools.kaiju.gradlezilla.models.*
import java.io.File

class GradleProjectInspector(
    private val projectDir: File,
    private val extractors: List<AgpDataExtractor> =
        listOf(
            InitScriptExtractor(),
            VersionCatalogExtractor(),
        ),
) {
    @Throws(GradleInspectorException::class)
    fun targets(daemonJdkOverride: File? = null): List<BuildTarget> {
        validateGradleProject()
        val daemonJdk = DaemonJdk.resolve(projectDir, daemonJdkOverride)

        return connected(daemonJdk.javaHome) { connection ->
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
    }

    @Throws(GradleInspectorException::class)
    fun inspect(daemonJdkOverride: File? = null): AndroidProjectSpec {
        validateGradleProject()
        val daemonJdk = DaemonJdk.resolve(projectDir, daemonJdkOverride)

        val daemonJvmCriteriaVersion = DaemonJvmCriteria.read(projectDir)
        return connected(daemonJdk.javaHome) { connection ->
            val env = fetchEnvironment(connection)
            val ctx = ExtractionContext(projectDir, connection, env)
            val agpData = executeExtractionChain(ctx)

            val jdkFacts = extractors.filterIsInstance<InitScriptExtractor>().firstOrNull()?.jdkFacts.orEmpty()
            val gradleVersion = GradleVersion.parse(env.gradleVersion)
            val agpVersion = agpData.agpVersion?.let(AgpVersion::parse)
            val resolution = JdkResolver.resolve(daemonJvmCriteriaVersion, jdkFacts, gradleVersion, agpVersion)
            val resolved =
                when (resolution) {
                    is JdkResolution.Resolved -> resolution
                    is JdkResolution.Unsupported -> throw GradleInspectorException(resolution.reason)
                }

            AndroidProjectSpec(
                jdkVersion = resolved.jdkVersion,
                gradleVersion = env.gradleVersion,
                androidSdkVersion = agpData.compileSdk,
                androidBuildToolsVersion = agpData.buildToolsVersion,
                androidNdkVersion = agpData.ndkVersion,
                androidCmakeVersion = agpData.cmakeVersion,
                modules = env.modules,
                hasBuildSrc = env.hasBuildSrc,
                hasBuildLogic = env.hasBuildLogic,
                extractionMetadata =
                    ExtractionMetadata(
                        gradleUserHome = connection.gradleUserHome.absolutePath,
                        projectCacheDir = connection.projectCacheDir.absolutePath,
                        daemonJavaHome = daemonJdk.javaHome.absolutePath,
                        daemonJdkSource = daemonJdk.source.wireName(),
                        jdkVersionSource = resolved.source.wireName(),
                        jdkVersionWarnings = resolved.warnings,
                        nativeBuildWarnings = agpData.nativeBuildWarnings,
                    ),
            )
        }
    }

    /** Runs [block] against a single pinned Gradle Tooling API connection for this project. */
    @Throws(GradleInspectorException::class)
    private fun <T> connected(
        javaHome: File,
        block: (PinnedConnection) -> T,
    ): T =
        try {
            when (val result = PinnedConnection.withConnection(projectDir, javaHome, block)) {
                is PinnedConnectionResult.Success -> result.value
                is PinnedConnectionResult.Rejected -> throw GradleInspectorException(result.reason)
            }
        } catch (e: GradleConnectionException) {
            throw GradleInspectorException(
                "Could not connect to Gradle project at '$projectDir': ${e::class.simpleName}::${e.message}",
                e,
            )
        }

    @Throws(GradleInspectorException::class)
    internal fun fetchEnvironment(connection: PinnedConnection): GradleProjectEnvironment {
        val hasBuildSrc = File(projectDir, "buildSrc").isDirectory
        val hasBuildLogic = File(projectDir, "build-logic").isDirectory

        try {
            val buildEnv = connection.getModel(BuildEnvironment::class.java)

            val gradleProject = connection.getModel(GradleProject::class.java)

            return GradleProjectEnvironment(
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

    @Suppress("TooGenericExceptionCaught")
    @Throws(GradleInspectorException::class)
    internal fun executeExtractionChain(context: ExtractionContext): AgpData {
        val attempts = mutableListOf<Pair<String, ExtractionOutcome>>()
        for (extractor in extractors) {
            val outcome =
                try {
                    extractor.extract(context)
                } catch (e: Exception) {
                    dumpDebugCauseChain(extractor.name, e)
                    ExtractionOutcome.Failed("${extractor.name} threw an unexpected error", e)
                }
            when (outcome) {
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
                            is ExtractionOutcome.NotApplicable -> {
                                outcome.reason
                            }

                            is ExtractionOutcome.Failed -> {
                                outcome.cause?.rootCause()?.message?.let { "${outcome.reason}: $it" }
                                    ?: outcome.reason
                            }

                            is ExtractionOutcome.Found -> {
                                error("unreachable")
                            }
                        }
                    appendLine("    $name: $reason")
                }
            },
        )
    }

    /** Under GRADLEZILLA_DEBUG=1, the full cause chain — the wrapper message alone often hides why. */
    private fun dumpDebugCauseChain(
        extractorName: String,
        e: Exception,
    ) {
        if (System.getenv("GRADLEZILLA_DEBUG") == null) return
        System.err.println("[$extractorName] failed with cause chain:")
        e.causeChain().forEach {
            System.err.println("  ${it::class.qualifiedName}: ${it.message}")
        }
    }

    private fun collectModules(project: GradleProject): List<ModuleSpec> =
        project.children.flatMap { child ->
            listOf(ModuleSpec(path = child.path, isApplication = child.tasks.any { it.name == "installDebug" })) +
                collectModules(child)
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
        val SETTINGS_FILES = listOf("settings.gradle.kts", "settings.gradle")
    }
}

private fun JdkVersionSource.wireName(): String =
    when (this) {
        JdkVersionSource.DAEMON_JVM_CRITERIA -> "daemonJvmCriteria"
        JdkVersionSource.TOOLCHAIN -> "toolchain"
        JdkVersionSource.BYTECODE_TARGET -> "bytecodeTarget"
        JdkVersionSource.AGP_MINIMUM -> "agpMinimum"
    }
