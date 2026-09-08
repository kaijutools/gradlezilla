package tools.kaiju.gradlezilla.cli

import kotlin.test.Test
import kotlin.test.assertEquals

class MainTest {
    @Test
    fun detectFormat_noFormatFlag_returnsHuman() {
        assertEquals("human", detectFormat(arrayOf("generate", "/some/path")))
    }

    @Test
    fun detectFormat_spaceSeparated_returnsValue() {
        assertEquals("json", detectFormat(arrayOf("generate", "--format", "json", "/some/path")))
        assertEquals("sarif", detectFormat(arrayOf("generate", "--format", "sarif", "/some/path")))
    }

    @Test
    fun detectFormat_equalsSeparated_returnsValue() {
        assertEquals("json", detectFormat(arrayOf("generate", "--format=json", "/some/path")))
    }

    @Test
    fun detectFormat_explicitHuman_returnsHuman() {
        assertEquals("human", detectFormat(arrayOf("generate", "--format", "human", "/some/path")))
    }

    @Test
    fun detectFormat_invalidValue_returnsHuman() {
        assertEquals("human", detectFormat(arrayOf("generate", "--format", "yaml", "/some/path")))
    }

    @Test
    fun detectFormat_trailingFlagWithNoValue_returnsHuman() {
        assertEquals("human", detectFormat(arrayOf("generate", "/some/path", "--format")))
    }
}
