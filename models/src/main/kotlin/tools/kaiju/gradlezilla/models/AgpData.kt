package tools.kaiju.gradlezilla.models

data class AgpData(
    val compileSdk: Int,
    val buildToolsVersion: String?,
    val ndkVersion: String?,
)
