package tools.kaiju.gradlezilla.models

data class ModuleJdkFacts(
    val modulePath: String,
    val toolchainVersion: Int?,
    val kotlinToolchainVersion: Int?,
    val javaTargetVersion: Int?,
    val kotlinTargetVersion: Int?,
)
