package tools.kaiju.gradlezilla.inspector

data class BuildTarget(
    val name: String,
    val path: String,
    val group: String?,
    val description: String?,
)
