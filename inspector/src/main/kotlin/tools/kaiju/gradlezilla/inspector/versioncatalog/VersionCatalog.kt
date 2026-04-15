package tools.kaiju.gradlezilla.inspector.versioncatalog

import kotlinx.serialization.Serializable

@Serializable
data class VersionCatalog(
    val versions: Map<String, String> = emptyMap(),
    val libraries: Map<String, Library> = emptyMap(),
    val plugins: Map<String, Plugin> = emptyMap(),
)
