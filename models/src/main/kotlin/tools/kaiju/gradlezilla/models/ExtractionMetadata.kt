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
    /**
     * The exact Gradle command line the init-script extraction ran with — the connection's
     * [PinnedConnection.pinnedArguments] plus the extractor's own. Surfaced so a surprising
     * extraction result can be diagnosed from `--format json` output alone, without
     * `GRADLEZILLA_DEBUG`. Empty when extraction never reached the init script.
     */
    val extractionArgs: List<String> = emptyList(),
)
