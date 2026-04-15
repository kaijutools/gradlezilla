package tools.kaiju.gradlezilla.generator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GeneratorSpecTest {
    private val baseSpec =
        GeneratorSpec(
            jdkVersion = "17",
            androidSdkVersion = 34,
            androidPlatformToolsVersion = "34.0.5",
            androidCommandLineToolsVersion = "11076708",
            androidNdkVersion = null,
        )

    @Test
    fun `required fields are stored correctly`() {
        val spec =
            GeneratorSpec(
                jdkVersion = "17",
                androidSdkVersion = 34,
                androidPlatformToolsVersion = "34.0.5",
                androidCommandLineToolsVersion = "11076708",
                androidNdkVersion = "25.1.8937393",
            )
        assertEquals("17", spec.jdkVersion)
        assertEquals(34, spec.androidSdkVersion)
        assertEquals("34.0.5", spec.androidPlatformToolsVersion)
        assertEquals("11076708", spec.androidCommandLineToolsVersion)
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
        val a = baseSpec.copy()
        val b = baseSpec.copy()
        assertEquals(a, b)
    }

    @Test
    fun `copy produces independent instance with overridden field`() {
        val modified = baseSpec.copy(jdkVersion = "21")
        assertEquals("21", modified.jdkVersion)
        assertEquals("17", baseSpec.jdkVersion)
    }
}
