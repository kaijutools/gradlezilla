package tools.kaiju.gradlezilla.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JdkResolverTest {
    private fun facts(
        modulePath: String = ":app",
        toolchainVersion: Int? = null,
        kotlinToolchainVersion: Int? = null,
        javaTargetVersion: Int? = null,
        kotlinTargetVersion: Int? = null,
    ) = ModuleJdkFacts(modulePath, toolchainVersion, kotlinToolchainVersion, javaTargetVersion, kotlinTargetVersion)

    @Test
    fun `uses daemon JVM criteria when no modules declare anything`() {
        val result =
            JdkResolver.resolve(
                daemonJvmCriteriaVersion = 21,
                moduleFacts = emptyList(),
                gradleVersion = null,
                agpVersion = null,
            )

        val resolved = assertIs<JdkResolution.Resolved>(result)
        assertEquals(21, resolved.jdkVersion)
        assertEquals(JdkVersionSource.DAEMON_JVM_CRITERIA, resolved.source)
        assertEquals(emptyList(), resolved.warnings)
    }

    @Test
    fun `uses the single toolchain version when all modules agree`() {
        val result =
            JdkResolver.resolve(
                daemonJvmCriteriaVersion = null,
                moduleFacts = listOf(facts(":app", toolchainVersion = 17), facts(":lib", toolchainVersion = 17)),
                gradleVersion = null,
                agpVersion = null,
            )

        val resolved = assertIs<JdkResolution.Resolved>(result)
        assertEquals(17, resolved.jdkVersion)
        assertEquals(JdkVersionSource.TOOLCHAIN, resolved.source)
        assertEquals(emptyList(), resolved.warnings)
    }

    @Test
    fun `pools daemon criteria and toolchains, attributing the source to daemon criteria when it ties the max`() {
        val result =
            JdkResolver.resolve(
                daemonJvmCriteriaVersion = 21,
                moduleFacts = listOf(facts(":app", toolchainVersion = 21)),
                gradleVersion = null,
                agpVersion = null,
            )

        val resolved = assertIs<JdkResolution.Resolved>(result)
        assertEquals(21, resolved.jdkVersion)
        assertEquals(JdkVersionSource.DAEMON_JVM_CRITERIA, resolved.source)
        assertEquals(emptyList(), resolved.warnings)
    }

    @Test
    fun `takes the max and warns when daemon criteria and a module toolchain disagree`() {
        val result =
            JdkResolver.resolve(
                daemonJvmCriteriaVersion = 17,
                moduleFacts = listOf(facts(":app", toolchainVersion = 21)),
                gradleVersion = null,
                agpVersion = null,
            )

        val resolved = assertIs<JdkResolution.Resolved>(result)
        assertEquals(21, resolved.jdkVersion)
        assertTrue(resolved.warnings.isNotEmpty(), "expected a conflict warning")
    }

    @Test
    fun `takes the max and warns when modules' toolchains disagree with each other`() {
        val result =
            JdkResolver.resolve(
                daemonJvmCriteriaVersion = null,
                moduleFacts = listOf(facts(":app", toolchainVersion = 21), facts(":lib", toolchainVersion = 17)),
                gradleVersion = null,
                agpVersion = null,
            )

        val resolved = assertIs<JdkResolution.Resolved>(result)
        assertEquals(21, resolved.jdkVersion)
        assertEquals(JdkVersionSource.TOOLCHAIN, resolved.source)
        assertTrue(resolved.warnings.single().contains(":app=21"))
        assertTrue(resolved.warnings.single().contains(":lib=17"))
    }

    @Test
    fun `falls back to AGP minimum JDK when nothing is declared`() {
        val result =
            JdkResolver.resolve(
                daemonJvmCriteriaVersion = null,
                moduleFacts = emptyList(),
                gradleVersion = null,
                agpVersion = AgpVersion(8, 0),
            )

        val resolved = assertIs<JdkResolution.Resolved>(result)
        assertEquals(17, resolved.jdkVersion)
        assertEquals(JdkVersionSource.AGP_MINIMUM, resolved.source)
    }

    @Test
    fun `falls back to the max bytecode target when it exceeds AGP's minimum`() {
        val result =
            JdkResolver.resolve(
                daemonJvmCriteriaVersion = null,
                moduleFacts = listOf(facts(":app", javaTargetVersion = 21)),
                gradleVersion = null,
                agpVersion = AgpVersion(7, 0),
            )

        val resolved = assertIs<JdkResolution.Resolved>(result)
        assertEquals(21, resolved.jdkVersion)
        assertEquals(JdkVersionSource.BYTECODE_TARGET, resolved.source)
    }

    @Test
    fun `rounds up to the next LTS rung rather than returning an exact floor`() {
        // AgpVersion(7, 0) has minimum JDK 11, which isn't itself an LTS rung in the ladder.
        val result =
            JdkResolver.resolve(
                daemonJvmCriteriaVersion = null,
                moduleFacts = emptyList(),
                gradleVersion = null,
                agpVersion = AgpVersion(7, 0),
            )

        val resolved = assertIs<JdkResolution.Resolved>(result)
        assertEquals(17, resolved.jdkVersion)
    }

    @Test
    fun `returns Unsupported when nothing is knowable`() {
        val result =
            JdkResolver.resolve(
                daemonJvmCriteriaVersion = null,
                moduleFacts = emptyList(),
                gradleVersion = null,
                agpVersion = null,
            )

        assertIs<JdkResolution.Unsupported>(result)
    }

    @Test
    fun `returns Unsupported when the resolved floor exceeds the known LTS ladder`() {
        val result =
            JdkResolver.resolve(
                daemonJvmCriteriaVersion = null,
                moduleFacts = listOf(facts(":app", javaTargetVersion = 30)),
                gradleVersion = null,
                agpVersion = null,
            )

        val unsupported = assertIs<JdkResolution.Unsupported>(result)
        assertTrue(unsupported.reason.contains("30"))
    }

    @Test
    fun `returns Unsupported when the resolved version exceeds what the project's Gradle version supports`() {
        // GradleVersion(7, 3) supports up to JDK 17 per GradleJdkCompatibility.
        val result =
            JdkResolver.resolve(
                daemonJvmCriteriaVersion = 21,
                moduleFacts = emptyList(),
                gradleVersion = GradleVersion(7, 3),
                agpVersion = null,
            )

        val unsupported = assertIs<JdkResolution.Unsupported>(result)
        assertTrue(unsupported.reason.contains("17"))
    }

    @Test
    fun `is fail-open on gradle version validation when the gradle version is null`() {
        val result =
            JdkResolver.resolve(
                daemonJvmCriteriaVersion = 21,
                moduleFacts = emptyList(),
                gradleVersion = null,
                agpVersion = null,
            )

        assertIs<JdkResolution.Resolved>(result)
    }
}
