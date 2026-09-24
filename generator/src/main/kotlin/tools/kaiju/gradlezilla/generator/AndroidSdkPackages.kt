package tools.kaiju.gradlezilla.generator

import tools.kaiju.gradlezilla.models.AndroidProjectSpec

/** Shared sdkmanager defaults and package-list rendering used by every Dockerfile generator. */
internal object AndroidSdkPackages {
    const val DEFAULT_CLI_TOOLS_VERSION = "11076708"
    const val DEFAULT_BUILD_TOOLS_VERSION = "34.0.0"

    fun render(spec: AndroidProjectSpec): String =
        buildList {
            add("\"platforms;android-${spec.androidSdkVersion}\"")
            add("\"platform-tools\"")
            add("\"build-tools;${spec.androidBuildToolsVersion ?: DEFAULT_BUILD_TOOLS_VERSION}\"")
            // Both are absent unless some module actually builds native code — installing an NDK
            // (~1GB+) for a project that never invokes it only slows the docker build and bloats
            // the image. See NativeBuildResolver.
            spec.androidNdkVersion?.let { add("\"ndk;$it\"") }
            spec.androidCmakeVersion?.let { add("\"cmake;$it\"") }
        }.joinToString(" ")
}
