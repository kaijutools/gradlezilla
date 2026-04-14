package tools.kaiju.gradlezilla.inspector

class GradleInspectorException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
