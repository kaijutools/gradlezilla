package tools.kaiju.gradlezilla.inspector.versioncatalog

import kotlinx.serialization.Serializable

@Serializable
data class Library(
    val module: String,
    val version: Version? = null,
)
