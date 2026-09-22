package tools.kaiju.gradlezilla.models

data class GradleProjectEnvironment(
    val gradleVersion: String,
    val gradleJvmArgs: String?,
    val modules: List<ModuleSpec>,
    val hasBuildSrc: Boolean,
    val hasBuildLogic: Boolean,
)
