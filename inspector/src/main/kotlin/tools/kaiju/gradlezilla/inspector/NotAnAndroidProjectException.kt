package tools.kaiju.gradlezilla.inspector

class NotAnAndroidProjectException(
    message: String,
    cause: Throwable? = null,
) : GradleInspectorException(message, cause)
