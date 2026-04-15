package tools.kaiju.gradlezilla.generator

class GeneratorException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
