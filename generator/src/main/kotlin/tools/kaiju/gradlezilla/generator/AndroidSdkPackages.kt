package tools.kaiju.gradlezilla.generator

import tools.kaiju.gradlezilla.models.AndroidProjectSpec

/** Shared sdkmanager defaults and package-list rendering used by every Dockerfile generator. */
internal object AndroidSdkPackages {
    const val DEFAULT_CLI_TOOLS_VERSION = "11076708"
    const val DEFAULT_BUILD_TOOLS_VERSION = "34.0.0"

    fun render(spec: AndroidProjectSpec): String =
        buildList {
            add("\"platforms;android-${spec.androidSdkVersion}\"")
            add("\"build-tools;${spec.androidBuildToolsVersion ?: DEFAULT_BUILD_TOOLS_VERSION}\"")
            spec.androidNdkVersion?.let { add("\"ndk;$it\"") }
        }.joinToString(" ")
}
