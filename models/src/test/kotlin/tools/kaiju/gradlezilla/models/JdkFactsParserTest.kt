package tools.kaiju.gradlezilla.models

import kotlin.test.Test
import kotlin.test.assertEquals

class JdkFactsParserTest {
    @Test
    fun `parses one JDK data line into a single ModuleJdkFacts`() {
        val output =
            "GRADLEZILLA_JDK_DATA::path=:app::toolchain=21::kotlinToolchain=21::javaTarget=17::kotlinTarget=17"

        val result = JdkFactsParser.parse(output)

        assertEquals(
            listOf(
                ModuleJdkFacts(
                    modulePath = ":app",
                    toolchainVersion = 21,
                    kotlinToolchainVersion = 21,
                    javaTargetVersion = 17,
                    kotlinTargetVersion = 17,
                ),
            ),
            result,
        )
    }

    @Test
    fun `parses multiple JDK data lines into one facts entry per module`() {
        val output =
            listOf(
                "GRADLEZILLA_JDK_DATA::path=:app::toolchain=21::kotlinToolchain=null::javaTarget=17::kotlinTarget=17",
                "GRADLEZILLA_JDK_DATA::path=:lib::toolchain=null::kotlinToolchain=null::javaTarget=11::kotlinTarget=11",
            ).joinToString("\n")

        val result = JdkFactsParser.parse(output)

        assertEquals(listOf(":app", ":lib"), result.map { it.modulePath })
    }

    @Test
    fun `returns an empty list when no line has the data prefix`() {
        assertEquals(emptyList(), JdkFactsParser.parse("some unrelated gradle log output"))
    }

    @Test
    fun `treats a malformed numeric value as null instead of dropping the line`() {
        val output =
            "GRADLEZILLA_JDK_DATA::path=:app::toolchain=not-a-number::" +
                "kotlinToolchain=null::javaTarget=null::kotlinTarget=null"

        val result = JdkFactsParser.parse(output)

        assertEquals(
            listOf(ModuleJdkFacts(":app", null, null, null, null)),
            result,
        )
    }

    @Test
    fun `skips a line with no path key`() {
        val output = "GRADLEZILLA_JDK_DATA::toolchain=21::kotlinToolchain=null::javaTarget=null::kotlinTarget=null"

        assertEquals(emptyList(), JdkFactsParser.parse(output))
    }
}
