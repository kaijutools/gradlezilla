package tools.kaiju.gradlezilla.inspector.versioncatalog

import kotlinx.serialization.Serializable

@Serializable
data class Plugin(
    val id: String? = null,
    val version: Version? = null,
)
