package tools.kaiju.gradlezilla.generator

data class Dockerfile(
    val layers: List<DockerLayer>,
) {
    fun render(): String =
        buildString {
            appendLine("# syntax=docker/dockerfile:1")
            layers.forEach { layer ->
                appendLine()
                append(layer.render())
            }
        }
}
