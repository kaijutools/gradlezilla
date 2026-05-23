package tools.kaiju.gradlezilla.cli.format

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tools.kaiju.gradlezilla.inspector.BuildTarget
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class InspectFormatterTest {
    private val targets = listOf(
        BuildTarget(name = "assembleDebug", path = ":app:assembleDebug", group = "build", description = "Assembles debug build"),
        BuildTarget(name = "test", path = ":app:test", group = "verification", description = "Runs tests"),
        BuildTarget(name = "help", path = ":help", group = null, description = null),
    )

    @Test
    fun humanFormat_groupsTargets() {
        val result = InspectFormatter.Human.format(targets)
        assertContains(result, "build")
        assertContains(result, ":app:assembleDebug")
        assertContains(result, "verification")
        assertContains(result, "(ungrouped)")
    }

    @Test
    fun humanFormat_includesDescriptions() {
        val result = InspectFormatter.Human.format(targets)
        assertContains(result, "Assembles debug build")
    }

    @Test
    fun humanFormat_emptyList_returnsEmpty() {
        val result = InspectFormatter.Human.format(emptyList())
        assertEquals("", result)
    }

    @Test
    fun jsonFormat_isJsonArray() {
        val result = InspectFormatter.JsonOutput.format(targets)
        val parsed = Json.parseToJsonElement(result).jsonArray
        assertEquals(3, parsed.size)
    }

    @Test
    fun jsonFormat_containsExpectedFields() {
        val result = InspectFormatter.JsonOutput.format(targets)
        val first = Json.parseToJsonElement(result).jsonArray.first().jsonObject
        assertEquals("assembleDebug", first["name"]?.jsonPrimitive?.content)
        assertEquals(":app:assembleDebug", first["path"]?.jsonPrimitive?.content)
        assertEquals("build", first["group"]?.jsonPrimitive?.content)
    }

    @Test
    fun sarifFormat_hasCorrectVersion() {
        val result = InspectFormatter.SarifOutput.format(targets)
        val obj = Json.parseToJsonElement(result).jsonObject
        assertEquals("2.1.0", obj["version"]?.jsonPrimitive?.content)
    }

    @Test
    fun sarifFormat_hasInformationalResults() {
        val result = InspectFormatter.SarifOutput.format(targets)
        val obj = Json.parseToJsonElement(result).jsonObject
        val run = obj["runs"]?.jsonArray?.first()?.jsonObject
        val results = run?.get("results")?.jsonArray
        assertNotNull(results)
        assertEquals(3, results.size)
        assertEquals("informational", results.first().jsonObject["kind"]?.jsonPrimitive?.content)
        assertEquals("build-target", results.first().jsonObject["ruleId"]?.jsonPrimitive?.content)
    }

    @Test
    fun forFormat_returnsCorrectFormatter() {
        assert(InspectFormatter.forFormat("human") is InspectFormatter.Human)
        assert(InspectFormatter.forFormat("json") is InspectFormatter.JsonOutput)
        assert(InspectFormatter.forFormat("sarif") is InspectFormatter.SarifOutput)
        assert(InspectFormatter.forFormat("unknown") is InspectFormatter.Human)
    }
}
