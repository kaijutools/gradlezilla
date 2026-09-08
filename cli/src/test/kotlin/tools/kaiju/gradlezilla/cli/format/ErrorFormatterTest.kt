package tools.kaiju.gradlezilla.cli.format

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class ErrorFormatterTest {
    private val message = "Could not extract Android configuration"

    @Test
    fun humanFormat_prependsError() {
        val result = ErrorFormatter.Human.format(message)
        assertEquals("Error: $message", result)
    }

    @Test
    fun jsonFormat_containsErrorField() {
        val result = ErrorFormatter.JsonOutput.format(message)
        val obj = Json.parseToJsonElement(result).jsonObject
        assertEquals(message, obj["error"]?.jsonPrimitive?.content)
    }

    @Test
    fun sarifFormat_hasCorrectVersion() {
        val result = ErrorFormatter.SarifOutput.format(message)
        val obj = Json.parseToJsonElement(result).jsonObject
        assertEquals("2.1.0", obj["version"]?.jsonPrimitive?.content)
    }

    @Test
    fun sarifFormat_hasNoResultsAndAFailedInvocation() {
        val result = ErrorFormatter.SarifOutput.format(message)
        val run =
            Json
                .parseToJsonElement(result)
                .jsonObject["runs"]
                ?.jsonArray
                ?.first()
                ?.jsonObject
        assertEquals(0, run?.get("results")?.jsonArray?.size)

        val invocation = run?.get("invocations")?.jsonArray?.first()?.jsonObject
        assertNotNull(invocation)
        assertFalse(invocation["executionSuccessful"]!!.jsonPrimitive.boolean)

        val notification =
            invocation["toolExecutionNotifications"]
                ?.jsonArray
                ?.first()
                ?.jsonObject
        assertEquals("error", notification?.get("level")?.jsonPrimitive?.content)
        assertContains(
            notification
                ?.get("message")
                ?.jsonObject
                ?.get("text")
                ?.jsonPrimitive
                ?.content ?: "",
            message,
        )
    }

    @Test
    fun forFormat_returnsCorrectFormatter() {
        assert(ErrorFormatter.forFormat("human") is ErrorFormatter.Human)
        assert(ErrorFormatter.forFormat("json") is ErrorFormatter.JsonOutput)
        assert(ErrorFormatter.forFormat("sarif") is ErrorFormatter.SarifOutput)
        assert(ErrorFormatter.forFormat("unknown") is ErrorFormatter.Human)
    }
}
