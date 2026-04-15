package tools.kaiju.gradlezilla.generator

data class GeneratorSpec(
    val jdkVersion: String,
    val androidSdkVersion: Int,
    val androidPlatformToolsVersion: String,
    val androidCommandLineToolsVersion: String,
    val androidNdkVersion: String?,
)
