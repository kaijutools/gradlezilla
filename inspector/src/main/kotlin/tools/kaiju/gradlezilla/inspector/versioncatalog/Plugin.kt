package tools.kaiju.gradlezilla.inspector.versioncatalog

import kotlinx.serialization.Serializable

@Serializable
data class Plugin(
    val id: String,
    val version: Version? = null,
)
