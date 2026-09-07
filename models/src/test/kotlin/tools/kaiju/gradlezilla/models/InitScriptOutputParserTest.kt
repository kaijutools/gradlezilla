package tools.kaiju.gradlezilla.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class InitScriptOutputParserTest {
    @Test
    fun `parses a normal data line`() {
        val output = "GRADLEZILLA_AGP_DATA::compileSdk=34::buildTools=34.0.0::ndk=25.1.8937393"
        val result = assertIs<InitScriptOutputParser.ParseOutcome.Success>(InitScriptOutputParser.parse(output))

        assertEquals(
            AgpData(compileSdk = 34, buildToolsVersion = "34.0.0", ndkVersion = "25.1.8937393"),
            result.data,
        )
    }

    @Test
    fun `preserves everything after the first equals sign in a value`() {
        val output = "GRADLEZILLA_AGP_DATA::compileSdk=34::buildTools=34.0.0::ndk=side=car"
        val result = assertIs<InitScriptOutputParser.ParseOutcome.Success>(InitScriptOutputParser.parse(output))

        assertEquals("side=car", result.data.ndkVersion)
    }

    @Test
    fun `skips a pair with no equals sign instead of crashing`() {
        val output = "GRADLEZILLA_AGP_DATA::compileSdk=34::garbage::buildTools=34.0.0"
        val result = assertIs<InitScriptOutputParser.ParseOutcome.Success>(InitScriptOutputParser.parse(output))

        assertEquals(AgpData(compileSdk = 34, buildToolsVersion = "34.0.0", ndkVersion = null), result.data)
    }

    @Test
    fun `returns NoDataLine for empty output`() {
        assertEquals(InitScriptOutputParser.ParseOutcome.NoDataLine, InitScriptOutputParser.parse(""))
    }

    @Test
    fun `returns NoDataLine when no line has the data prefix`() {
        val output = "some unrelated gradle log output\nanother line"
        assertEquals(InitScriptOutputParser.ParseOutcome.NoDataLine, InitScriptOutputParser.parse(output))
    }

    @Test
    fun `succeeds with only some keys present`() {
        val output = "GRADLEZILLA_AGP_DATA::compileSdk=34"
        val result = assertIs<InitScriptOutputParser.ParseOutcome.Success>(InitScriptOutputParser.parse(output))

        assertEquals(AgpData(compileSdk = 34, buildToolsVersion = null, ndkVersion = null), result.data)
    }

    @Test
    fun `returns MissingCompileSdk when the data line has no compileSdk key`() {
        val output = "GRADLEZILLA_AGP_DATA::buildTools=34.0.0::ndk=25.1.8937393"
        val result =
            assertIs<InitScriptOutputParser.ParseOutcome.MissingCompileSdk>(InitScriptOutputParser.parse(output))

        assertEquals("buildTools=34.0.0::ndk=25.1.8937393", result.dataLine)
    }

    @Test
    fun `returns UnparseableCompileSdk when the value is not a number`() {
        val output = "GRADLEZILLA_AGP_DATA::compileSdk=android-Baklava::buildTools=34.0.0"
        val result =
            assertIs<InitScriptOutputParser.ParseOutcome.UnparseableCompileSdk>(InitScriptOutputParser.parse(output))

        assertEquals("android-Baklava", result.rawValue)
    }
}
