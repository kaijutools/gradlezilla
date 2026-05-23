package tools.kaiju.gradlezilla.cli.format

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tools.kaiju.gradlezilla.models.AndroidProjectSpec
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GenerateFormatterTest {
    private val spec = AndroidProjectSpec(
        jdkVersion = 17,
        androidSdkVersion = 34,
        androidCommandLineToolsVersion = "11076708",
        androidPlatformToolsVersion = "34.0.5",
        androidNdkVersion = null,
        gradleVersion = "8.4",
    )
    private val dockerfile = "FROM ubuntu:22.04\nRUN echo hello"

    @Test
    fun humanFormat_containsSpecSummary() {
        val result = GenerateFormatter.Human.format(spec, dockerfile, outputPath = null)
        assertContains(result, "JDK: 17")
        assertContains(result, "Android sdk: 34")
        assertContains(result, "DRY RUN")
        assertContains(result, dockerfile)
    }

    @Test
    fun humanFormat_withOutputPath_showsPath() {
        val result = GenerateFormatter.Human.format(spec, dockerfile, outputPath = "/tmp/Dockerfile")
        assertContains(result, "/tmp/Dockerfile")
    }

    @Test
    fun jsonFormat_isValidJson() {
        val result = GenerateFormatter.JsonOutput.format(spec, dockerfile, outputPath = null)
        val parsed = Json.parseToJsonElement(result)
        assertNotNull(parsed)
    }

    @Test
    fun jsonFormat_containsDockerfileAndSpec() {
        val result = GenerateFormatter.JsonOutput.format(spec, dockerfile, outputPath = "/tmp/Dockerfile")
        val obj = Json.parseToJsonElement(result).jsonObject
        assertEquals(dockerfile, obj["dockerfile"]?.jsonPrimitive?.content)
        assertEquals("/tmp/Dockerfile", obj["outputPath"]?.jsonPrimitive?.content)
        assertNotNull(obj["spec"])
    }

    @Test
    fun sarifFormat_hasCorrectVersion() {
        val result = GenerateFormatter.SarifOutput.format(spec, dockerfile, outputPath = null)
        val obj = Json.parseToJsonElement(result).jsonObject
        assertEquals("2.1.0", obj["version"]?.jsonPrimitive?.content)
    }

    @Test
    fun sarifFormat_containsDockerfileAsArtifact() {
        val result = GenerateFormatter.SarifOutput.format(spec, dockerfile, outputPath = null)
        val obj = Json.parseToJsonElement(result).jsonObject
        val run = obj["runs"]?.jsonArray?.first()?.jsonObject
        val artifact = run?.get("artifacts")?.jsonArray?.first()?.jsonObject
        val contents = artifact?.get("contents")?.jsonObject
        assertEquals(dockerfile, contents?.get("text")?.jsonPrimitive?.content)
    }

    @Test
    fun sarifFormat_specFieldsInProperties() {
        val result = GenerateFormatter.SarifOutput.format(spec, dockerfile, outputPath = null)
        val obj = Json.parseToJsonElement(result).jsonObject
        val run = obj["runs"]?.jsonArray?.first()?.jsonObject
        val props = run?.get("properties")?.jsonObject
        assertEquals("34", props?.get("androidSdkVersion")?.jsonPrimitive?.content)
    }

    @Test
    fun forFormat_returnsCorrectFormatter() {
        assert(GenerateFormatter.forFormat("human") is GenerateFormatter.Human)
        assert(GenerateFormatter.forFormat("json") is GenerateFormatter.JsonOutput)
        assert(GenerateFormatter.forFormat("sarif") is GenerateFormatter.SarifOutput)
        assert(GenerateFormatter.forFormat("unknown") is GenerateFormatter.Human)
    }
}
