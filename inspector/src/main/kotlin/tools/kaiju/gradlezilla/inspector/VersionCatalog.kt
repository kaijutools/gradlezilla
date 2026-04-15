package tools.kaiju.gradlezilla.inspector

import kotlinx.serialization.Serializable

@Serializable
data class VersionCatalog(
    val versions: Map<String, String> = emptyMap(),
    val libraries: Map<String, Library> = emptyMap(),
    val plugins: Map<String, Plugin> = emptyMap(),
)

@Serializable
data class Library(
    val module: String,
    val version: Version,
)

@Serializable
data class Plugin(
    val id: String,
    val version: Version,
)

@Serializable
data class Version(
    val ref: String? = null,
    val literal: String? = null,
)
