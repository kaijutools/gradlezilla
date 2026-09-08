package tools.kaiju.gradlezilla.inspector

import kotlinx.serialization.Serializable

@Serializable
data class BuildTarget(
    val name: String,
    val path: String,
    val group: String?,
    val description: String?,
)
