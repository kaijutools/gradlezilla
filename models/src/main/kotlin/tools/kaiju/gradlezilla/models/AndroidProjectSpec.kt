package tools.kaiju.gradlezilla.models

data class AndroidProjectSpec(
    val jdkVersion: Int = 17,
    val androidSdkVersion: Int,
    val androidPlatformToolsVersion: String,
    val androidCommandLineToolsVersion: String? = null,
    val androidNdkVersion: String? = null,
    val androidCmakeVersion: String? = null,
    val gradleVersion: String? = null,
    val gradleJvmArgs: String? = null,
    val modules: List<ModuleSpec> = emptyList(),
    val hasBuildSrc: Boolean = false,
    val hasBuildLogic: Boolean = false,
)
