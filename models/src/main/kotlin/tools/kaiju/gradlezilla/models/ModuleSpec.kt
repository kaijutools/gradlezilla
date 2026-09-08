package tools.kaiju.gradlezilla.models

import kotlinx.serialization.Serializable

@Serializable
data class ModuleSpec(
    val path: String,
    val isApplication: Boolean = false,
) {
    val dir: String get() = path.removePrefix(":").replace(":", "/")
}
