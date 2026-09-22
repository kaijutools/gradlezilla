package tools.kaiju.gradlezilla.inspector.initscript

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InitScriptExtractorTest {
    @Test
    fun supportsConfigurationCacheFlag_omitsFlagBelowGradle6_6() {
        assertFalse(supportsConfigurationCacheFlag("6.5"))
        assertFalse(supportsConfigurationCacheFlag("5.0"))
    }

    @Test
    fun supportsConfigurationCacheFlag_includesFlagAtGradle6_6AndAbove() {
        assertTrue(supportsConfigurationCacheFlag("6.6"))
        assertTrue(supportsConfigurationCacheFlag("8.1"))
        assertTrue(supportsConfigurationCacheFlag("9.7.1"))
    }

    @Test
    fun supportsConfigurationCacheFlag_failsOpenForUnparseableVersion() {
        assertFalse(supportsConfigurationCacheFlag("not-a-version"))
    }
}
