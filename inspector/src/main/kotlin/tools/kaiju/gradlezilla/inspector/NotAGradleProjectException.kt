package tools.kaiju.gradlezilla.inspector

class NotAGradleProjectException(
    message: String,
    cause: Throwable? = null,
) : GradleInspectorException(message, cause)
