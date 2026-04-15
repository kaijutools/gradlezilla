package tools.kaiju.gradlezilla.generator

import tools.kaiju.gradlezilla.models.AndroidProjectSpec

interface Generator {
    fun generate(spec: AndroidProjectSpec): Dockerfile
}
