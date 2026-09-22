package tools.kaiju.gradlezilla.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgpJdkCompatibilityTest {
    @Test
    fun `returns the minimum JDK for a known AGP version`() {
        assertEquals(17, AgpJdkCompatibility.minimumJdk(AgpVersion(8, 5)))
        assertEquals(11, AgpJdkCompatibility.minimumJdk(AgpVersion(7, 2)))
    }

    @Test
    fun `returns null for an AGP version older than the table`() {
        assertNull(AgpJdkCompatibility.minimumJdk(AgpVersion(4, 2)))
    }

    @Test
    fun `AgpVersion parse handles a pre-release suffix like 8_5_0-rc01`() {
        assertEquals(AgpVersion(8, 5), AgpVersion.parse("8.5.0-rc01"))
    }

    @Test
    fun `AgpVersion parse returns null for an unrecognisable string`() {
        assertNull(AgpVersion.parse("not-a-version"))
    }
}
