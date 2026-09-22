package tools.kaiju.gradlezilla.generator

import tools.kaiju.gradlezilla.models.AndroidProjectSpec

class DockerfileGenerator : Generator {
    override fun generate(spec: AndroidProjectSpec): String {
        val template =
            this::class.java.getResource("/Dockerfile.template")?.readText()
                ?: error("Fatal: Dockerfile.template not found")

        return template
            .replace("{{JDK_VERSION}}", spec.jdkVersion.toString())
            .replace(
                "{{CMDLINE_TOOLS_VERSION}}",
                spec.androidCommandLineToolsVersion ?: AndroidSdkPackages.DEFAULT_CLI_TOOLS_VERSION,
            ).replace("{{SDK_PACKAGES}}", AndroidSdkPackages.render(spec))
    }
}
