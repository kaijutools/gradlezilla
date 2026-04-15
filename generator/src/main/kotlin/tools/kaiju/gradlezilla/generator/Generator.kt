package tools.kaiju.gradlezilla.generator

interface Generator {
    fun generate(spec: GeneratorSpec): Dockerfile
}
