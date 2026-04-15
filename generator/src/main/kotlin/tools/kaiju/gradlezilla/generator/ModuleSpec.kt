package tools.kaiju.gradlezilla.generator

data class ModuleSpec(
    val path: String,
    val isApplication: Boolean = false,
) {
    val dir: String get() = path.removePrefix(":").replace(":", "/")
}
