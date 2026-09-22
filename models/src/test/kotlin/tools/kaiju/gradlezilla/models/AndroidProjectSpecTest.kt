package tools.kaiju.gradlezilla.models

import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidProjectSpecTest {
    private val baseSpec =
        AndroidProjectSpec(
            androidSdkVersion = 34,
            androidBuildToolsVersion = "34.0.5",
            androidCommandLineToolsVersion = "11076708",
        )

    @Test
    fun `required fields are stored correctly`() {
        val spec =
            AndroidProjectSpec(
                jdkVersion = 17,
                androidSdkVersion = 34,
                androidBuildToolsVersion = "34.0.5",
                androidCommandLineToolsVersion = "11076708",
            )
        assertEquals(17, spec.jdkVersion)
        assertEquals(34, spec.androidSdkVersion)
        assertEquals("34.0.5", spec.androidBuildToolsVersion)
        assertEquals("11076708", spec.androidCommandLineToolsVersion)
    }

    @Test
    fun `androidCommandLineToolsVersion is null when not provided`() {
        val spec = AndroidProjectSpec(androidSdkVersion = 34, androidBuildToolsVersion = "34.0.5")
        assertNull(spec.androidCommandLineToolsVersion)
    }

    @Test
    fun `androidNdkVersion is null when not provided`() {
        assertNull(baseSpec.androidNdkVersion)
    }

    @Test
    fun `androidNdkVersion is stored when provided`() {
        val spec = baseSpec.copy(androidNdkVersion = "25.1.8937393")
        assertEquals("25.1.8937393", spec.androidNdkVersion)
    }

    @Test
    fun `two specs with identical values are equal`() {
        assertEquals(baseSpec.copy(), baseSpec.copy())
    }

    @Test
    fun `copy produces independent instance with overridden field`() {
        val modified = baseSpec.copy(jdkVersion = 21)
        assertEquals(21, modified.jdkVersion)
        assertEquals(17, baseSpec.jdkVersion)
    }

    @Test
    fun `jdkVersion is present in JSON even when it equals its default value`() {
        // Json's encodeDefaults defaults to false — without @EncodeDefault(ALWAYS) on
        // jdkVersion, this field silently vanishes from output whenever it resolves to 17.
        val spec = baseSpec.copy(jdkVersion = 17)

        val json = Json.encodeToJsonElement(spec).jsonObject

        assertEquals(17, json["jdkVersion"]?.jsonPrimitive?.int)
    }

    @Test
    fun `every declared spec and metadata field is present in serialized JSON output`() {
        val spec =
            AndroidProjectSpec(
                jdkVersion = 17,
                androidSdkVersion = 34,
                androidBuildToolsVersion = "34.0.5",
                androidCommandLineToolsVersion = "11076708",
                androidNdkVersion = "25.1.8937393",
                androidCmakeVersion = "3.22.1",
                gradleVersion = "8.5",
                gradleJvmArgs = "-Xmx4g",
                modules = listOf(ModuleSpec(path = ":app", isApplication = true)),
                hasBuildSrc = true,
                hasBuildLogic = true,
                extractionMetadata =
                    ExtractionMetadata(
                        gradleUserHome = "/home/.gradle",
                        daemonJavaHome = "/opt/jdk-21",
                        jdkVersionSource = "agpMinimum",
                        jdkVersionWarnings = listOf("some warning"),
                    ),
            )

        val json = Json.encodeToJsonElement(spec).jsonObject
        for (name in AndroidProjectSpec.serializer().descriptor.elementNames) {
            assertTrue(json.containsKey(name), "expected spec JSON to contain field '$name', got keys: ${json.keys}")
        }

        val metadataJson = json.getValue("extractionMetadata").jsonObject
        for (name in ExtractionMetadata.serializer().descriptor.elementNames) {
            assertTrue(
                metadataJson.containsKey(name),
                "expected extractionMetadata JSON to contain field '$name', got keys: ${metadataJson.keys}",
            )
        }
    }
}
