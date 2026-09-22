package tools.kaiju.gradlezilla.models

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AndroidProjectSpec(
    // Json's encodeDefaults is off by default, which would silently drop this field from
    // --format json/sarif output whenever the resolved version equals the class default (17) —
    // a very common real value, unlike most of this class's other optional fields.
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val jdkVersion: Int = 17,
    val androidSdkVersion: Int,
    val androidBuildToolsVersion: String? = null,
    val androidCommandLineToolsVersion: String? = null,
    val androidNdkVersion: String? = null,
    val androidCmakeVersion: String? = null,
    val gradleVersion: String? = null,
    val gradleJvmArgs: String? = null,
    val modules: List<ModuleSpec> = emptyList(),
    val hasBuildSrc: Boolean = false,
    val hasBuildLogic: Boolean = false,
    val extractionMetadata: ExtractionMetadata? = null,
)
