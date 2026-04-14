package tools.kaiju.gradlezilla.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VersionTest {

    @Test
    fun `makeVersion returns expected format`() {
        val result = Version().makeVersion()
        assertEquals("Current version: ${BuildConfig.VERSION} - ${BuildConfig.COMMIT_SHA}", result)
    }

    @Test
    fun `makeVersion contains semver`() {
        assertTrue(Version().makeVersion().contains(BuildConfig.VERSION))
    }
}
