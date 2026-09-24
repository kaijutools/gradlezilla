package tools.kaiju.gradlezilla.models

import kotlinx.serialization.Serializable

@Serializable
data class ExtractionMetadata(
    val gradleUserHome: String,
    val projectCacheDir: String,
    val daemonJavaHome: String,
    val daemonJdkSource: String,
    val jdkVersionSource: String,
    val jdkVersionWarnings: List<String> = emptyList(),
    val nativeBuildWarnings: List<String> = emptyList(),
)
