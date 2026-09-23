package tools.kaiju.gradlezilla.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GradleJdkCompatibilityTest {
    @Test
    fun `maxSupportedJdk for an old Gradle version`() {
        assertEquals(11, GradleJdkCompatibility.maxSupportedJdk(GradleVersion(5, 0)))
    }

    @Test
    fun `maxSupportedJdk for Gradle 8_0 and 8_1 is JDK 19`() {
        assertEquals(19, GradleJdkCompatibility.maxSupportedJdk(GradleVersion(8, 0)))
        assertEquals(19, GradleJdkCompatibility.maxSupportedJdk(GradleVersion(8, 1)))
    }

    @Test
    fun `maxSupportedJdk is null for a Gradle version newer than the table`() {
        assertNull(GradleJdkCompatibility.maxSupportedJdk(GradleVersion(99, 0)))
    }

    @Test
    fun `check reports an incompatibility when the running JDK exceeds the max`() {
        val incompatibility = GradleJdkCompatibility.check(GradleVersion(8, 1), runningJdk = 21)

        assertEquals(19, incompatibility?.maxSupportedJdk)
    }

    @Test
    fun `check is null when the running JDK is within range`() {
        assertNull(GradleJdkCompatibility.check(GradleVersion(8, 1), runningJdk = 17))
    }
}
