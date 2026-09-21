package tools.kaiju.gradlezilla.models

import kotlinx.serialization.Serializable

@Serializable
data class ExtractionMetadata(
    val gradleUserHome: String,
    val daemonJavaHome: String,
)
