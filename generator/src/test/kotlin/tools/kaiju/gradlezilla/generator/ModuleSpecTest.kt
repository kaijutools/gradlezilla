package tools.kaiju.gradlezilla.generator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModuleSpecTest {
    @Test
    fun `dir strips leading colon from single-segment path`() {
        assertEquals("app", ModuleSpec(path = ":app").dir)
    }

    @Test
    fun `dir converts colons to slashes for nested modules`() {
        assertEquals("feature/login", ModuleSpec(path = ":feature:login").dir)
    }

    @Test
    fun `isApplication defaults to false`() {
        val spec = ModuleSpec(path = ":core:network")
        assertTrue(!spec.isApplication)
    }
}
