package tools.kaiju.gradlezilla.inspector.versioncatalog

import kotlinx.serialization.Serializable

@Serializable
data class Library(
    val module: String? = null,
    val group: String? = null,
    val name: String? = null,
    val version: Version? = null,
)
