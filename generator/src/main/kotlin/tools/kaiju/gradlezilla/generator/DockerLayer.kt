package tools.kaiju.gradlezilla.generator

data class DockerLayer(
    val instruction: DockerInstruction,
    val entries: List<String>,
    val next: DockerLayer? = null,
)
