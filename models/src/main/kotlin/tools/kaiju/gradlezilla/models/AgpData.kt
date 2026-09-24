package tools.kaiju.gradlezilla.models

data class AgpData(
    val compileSdk: Int,
    val buildToolsVersion: String?,
    /**
     * The one source of truth for this project's native toolchain needs.
     *
     * Deliberately not a pair of nullable version fields: "no NDK" and "an NDK we could not
     * determine" are different answers, and only an extractor that can actually observe
     * `externalNativeBuild` is entitled to give the first one. An extractor that cannot see it
     * reports [NativeBuildOutcome.NotApplicable] naming itself, which keeps a fallback from
     * silently reintroducing the false positive this whole path exists to remove.
     */
    val nativeBuild: NativeBuildOutcome,
    val agpVersion: String? = null,
) {
    val ndkVersion: String? get() = found?.ndkVersion
    val cmakeVersion: String? get() = found?.cmakeVersion
    val nativeBuildWarnings: List<String> get() = found?.warnings.orEmpty()

    private val found: NativeBuildOutcome.Found? get() = nativeBuild as? NativeBuildOutcome.Found
}
