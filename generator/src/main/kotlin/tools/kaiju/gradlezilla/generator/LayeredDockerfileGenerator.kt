package tools.kaiju.gradlezilla.generator

import tools.kaiju.gradlezilla.models.AndroidProjectSpec

/**
 * Generates a Dockerfile that copies Gradle build files and resolves dependencies in a layer
 * separate from the application source, so Docker's build cache survives source-only edits.
 */
class LayeredDockerfileGenerator : Generator {
    override fun generate(spec: AndroidProjectSpec): String {
        val template =
            this::class.java.getResource("/LayeredDockerfile.template")?.readText()
                ?: error("Fatal: LayeredDockerfile.template not found")

        val additionalCopies =
            buildString {
                if (spec.hasBuildSrc) appendLine("COPY buildSrc buildSrc")
                if (spec.hasBuildLogic) appendLine("COPY build-logic build-logic")
                spec.modules.forEach { appendLine("COPY ${it.dir}/build.gradle* ${it.dir}/") }
            }.trimEnd('\n')

        val dependencyWarmupTasks =
            spec.modules
                .takeIf { it.isNotEmpty() }
                ?.joinToString(" ") { "${it.path}:dependencies" }
                ?: "dependencies"

        return template
            .replace("{{JDK_VERSION}}", spec.jdkVersion.toString())
            .replace(
                "{{CMDLINE_TOOLS_VERSION}}",
                spec.androidCommandLineToolsVersion ?: AndroidSdkPackages.DEFAULT_CLI_TOOLS_VERSION,
            ).replace("{{SDK_PACKAGES}}", AndroidSdkPackages.render(spec))
            .replace("{{ADDITIONAL_COPIES}}", additionalCopies)
            .replace("{{DEPENDENCY_WARMUP_TASKS}}", dependencyWarmupTasks)
    }
}
