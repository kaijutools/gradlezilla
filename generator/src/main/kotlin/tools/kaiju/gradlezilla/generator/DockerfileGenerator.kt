package tools.kaiju.gradlezilla.generator

import tools.kaiju.gradlezilla.models.AndroidProjectSpec

class DockerfileGenerator : Generator {
    override fun generate(spec: AndroidProjectSpec): String {
        val template =
            this::class.java.getResource("/Dockerfile.template")?.readText()
                ?: error("Fatal: Dockerfile.template not found")

        val sdkPackages =
            buildList {
                add("\"platforms;android-${spec.androidSdkVersion}\"")
                add("\"build-tools;${spec.androidPlatformToolsVersion ?: DEFAULT_BUILD_TOOLS_VERSION}\"")
                spec.androidNdkVersion?.let { add("\"ndk;$it\"") }
            }.joinToString(" ")

        return template
            .replace("{{JDK_VERSION}}", spec.jdkVersion.toString())
            .replace("{{CMDLINE_TOOLS_VERSION}}", spec.androidCommandLineToolsVersion ?: DEFAULT_CLI_TOOLS_VERSION)
            .replace("{{SDK_PACKAGES}}", sdkPackages)
    }

    private companion object {
        const val DEFAULT_CLI_TOOLS_VERSION = "11076708"
        const val DEFAULT_BUILD_TOOLS_VERSION = "34.0.0"
    }
}
