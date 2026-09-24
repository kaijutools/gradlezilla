package tools.kaiju.gradlezilla.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class InitScriptOutputParserTest {
    @Test
    fun `parses a normal data line`() {
        val output =
            "GRADLEZILLA_AGP_DATA::path=:app::compileSdk=34::buildTools=34.0.0::ndk=25.1.8937393" +
                "::nativeBuild=ndkBuild"
        val result = assertIs<InitScriptOutputParser.ParseOutcome.Success>(InitScriptOutputParser.parse(output))

        assertEquals(
            AgpData(compileSdk = 34, buildToolsVersion = "34.0.0", ndkVersion = "25.1.8937393"),
            result.data,
        )
    }

    @Test
    fun `drops AGP's reported ndkVersion when no module configures externalNativeBuild`() {
        // AGP fills android.ndkVersion in with its bundled default on every project, so the value
        // being present says nothing about whether the build actually needs an NDK.
        val output = "GRADLEZILLA_AGP_DATA::path=:app::compileSdk=34::buildTools=34.0.0::ndk=25.1.8937393"
        val result = assertIs<InitScriptOutputParser.ParseOutcome.Success>(InitScriptOutputParser.parse(output))

        assertNull(result.data.ndkVersion)
        assertNull(result.data.cmakeVersion)
    }

    @Test
    fun `resolves native config across modules, not just the first line`() {
        val output =
            "GRADLEZILLA_AGP_DATA::path=:app::compileSdk=34::ndk=25.1.8937393\n" +
                "GRADLEZILLA_AGP_DATA::path=:native::compileSdk=34::ndk=26.1.10909125" +
                "::nativeBuild=cmake::cmakeVersion=3.22.1"
        val result = assertIs<InitScriptOutputParser.ParseOutcome.Success>(InitScriptOutputParser.parse(output))

        assertEquals("26.1.10909125", result.data.ndkVersion)
        assertEquals("3.22.1", result.data.cmakeVersion)
    }

    @Test
    fun `falls back to AGP's own default cmake version reported by the init script`() {
        val output =
            "GRADLEZILLA_AGP_DATA::path=:native::compileSdk=34::ndk=26.1.10909125" +
                "::nativeBuild=cmake::defaultCmake=3.30.5"
        val result = assertIs<InitScriptOutputParser.ParseOutcome.Success>(InitScriptOutputParser.parse(output))

        assertEquals("3.30.5", result.data.cmakeVersion)
    }

    @Test
    fun `preserves everything after the first equals sign in a value`() {
        val output =
            "GRADLEZILLA_AGP_DATA::path=:app::compileSdk=34::buildTools=34.0.0::nativeBuild=ndkBuild::ndk=side=car"
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

    @Test
    fun `parses agpVersion when present in the data line`() {
        val output = "GRADLEZILLA_AGP_DATA::compileSdk=34::buildTools=34.0.0::ndk=null::agpVersion=8.5.0"
        val result = assertIs<InitScriptOutputParser.ParseOutcome.Success>(InitScriptOutputParser.parse(output))

        assertEquals("8.5.0", result.data.agpVersion)
    }
}
