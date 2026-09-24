package tools.kaiju.gradlezilla.models

data class AgpData(
    val compileSdk: Int,
    val buildToolsVersion: String?,
    /**
     * Set only when some module actually builds native code — see [NativeBuildResolver].
     * Deliberately *not* AGP's `android.ndkVersion` as reported, which is populated with AGP's
     * bundled default regardless of whether the project has any native sources.
     */
    val ndkVersion: String?,
    val agpVersion: String? = null,
    val cmakeVersion: String? = null,
    val nativeBuildWarnings: List<String> = emptyList(),
)
