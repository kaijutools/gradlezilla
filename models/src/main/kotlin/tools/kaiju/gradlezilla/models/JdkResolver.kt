package tools.kaiju.gradlezilla.models

enum class JdkVersionSource {
    DAEMON_JVM_CRITERIA,
    TOOLCHAIN,
    BYTECODE_TARGET,
    AGP_MINIMUM,
}

sealed class JdkResolution {
    data class Resolved(
        val jdkVersion: Int,
        val source: JdkVersionSource,
        val warnings: List<String> = emptyList(),
    ) : JdkResolution()

    data class Unsupported(
        val reason: String,
    ) : JdkResolution()
}

/**
 * Resolves the JDK version a project requires from what it declares — never from whatever
 * JVM happens to be running the extraction daemon.
 */
object JdkResolver {
    @Suppress("MagicNumber")
    private val LTS_LADDER = listOf(17, 21, 25)
    private const val DAEMON_JVM_CRITERIA_LABEL = "daemon JVM criteria"

    fun resolve(
        daemonJvmCriteriaVersion: Int?,
        moduleFacts: List<ModuleJdkFacts>,
        gradleVersion: GradleVersion?,
        agpVersion: AgpVersion?,
    ): JdkResolution {
        val declared = pooledDeclarations(daemonJvmCriteriaVersion, moduleFacts)

        val (candidate, source, warnings) =
            if (declared.isNotEmpty()) {
                resolveFromDeclared(declared)
            } else {
                when (val result = resolveFromFloor(moduleFacts, agpVersion)) {
                    is FloorResult.NoSignal ->
                        return JdkResolution.Unsupported(
                            "Could not determine a JDK version: no daemon JVM criteria, no toolchain " +
                                "declarations, no bytecode targets declared, and no usable AGP version",
                        )

                    is FloorResult.ExceedsLtsLadder ->
                        return JdkResolution.Unsupported(
                            "Resolved JDK floor ${result.floor} exceeds the highest known LTS JDK " +
                                "(${LTS_LADDER.last()})",
                        )

                    is FloorResult.Found -> Triple(result.candidate, result.source, emptyList())
                }
            }

        validateAgainstGradle(candidate, gradleVersion)?.let { return it }

        return JdkResolution.Resolved(candidate, source, warnings)
    }

    private data class Labeled(
        val label: String,
        val version: Int,
    )

    private fun pooledDeclarations(
        daemonJvmCriteriaVersion: Int?,
        moduleFacts: List<ModuleJdkFacts>,
    ): List<Labeled> =
        buildList {
            daemonJvmCriteriaVersion?.let { add(Labeled(DAEMON_JVM_CRITERIA_LABEL, it)) }
            moduleFacts.forEach { fact ->
                fact.toolchainVersion?.let { add(Labeled(fact.modulePath, it)) }
                fact.kotlinToolchainVersion?.let { add(Labeled(fact.modulePath, it)) }
            }
        }

    private fun resolveFromDeclared(declared: List<Labeled>): Triple<Int, JdkVersionSource, List<String>> {
        val candidate = declared.maxOf { it.version }
        val distinctValues = declared.map { it.version }.distinct()

        // Daemon JVM criteria is Gradle's own explicit, build-wide pin — attribute the source to
        // it whenever it ties the winning candidate, even if module toolchains also agree.
        val source =
            if (declared.any { it.label == DAEMON_JVM_CRITERIA_LABEL && it.version == candidate }) {
                JdkVersionSource.DAEMON_JVM_CRITERIA
            } else {
                JdkVersionSource.TOOLCHAIN
            }

        val warnings =
            if (distinctValues.size > 1) {
                val detail = declared.joinToString(", ") { "${it.label}=${it.version}" }
                listOf("Conflicting declared JDK versions ($detail) — using max ($candidate)")
            } else {
                emptyList()
            }

        return Triple(candidate, source, warnings)
    }

    private sealed class FloorResult {
        data object NoSignal : FloorResult()

        data class ExceedsLtsLadder(
            val floor: Int,
        ) : FloorResult()

        data class Found(
            val candidate: Int,
            val source: JdkVersionSource,
        ) : FloorResult()
    }

    private fun resolveFromFloor(
        moduleFacts: List<ModuleJdkFacts>,
        agpVersion: AgpVersion?,
    ): FloorResult {
        val bytecodeFloor =
            (moduleFacts.mapNotNull { it.javaTargetVersion } + moduleFacts.mapNotNull { it.kotlinTargetVersion })
                .maxOrNull()
        val agpFloor = agpVersion?.let(AgpJdkCompatibility::minimumJdk)

        if (bytecodeFloor == null && agpFloor == null) return FloorResult.NoSignal

        val floor = maxOf(bytecodeFloor ?: 0, agpFloor ?: 0)
        val candidate = LTS_LADDER.firstOrNull { it >= floor } ?: return FloorResult.ExceedsLtsLadder(floor)
        val source =
            if (bytecodeFloor != null && bytecodeFloor > (agpFloor ?: 0)) {
                JdkVersionSource.BYTECODE_TARGET
            } else {
                JdkVersionSource.AGP_MINIMUM
            }

        return FloorResult.Found(candidate, source)
    }

    private fun validateAgainstGradle(
        candidate: Int,
        gradleVersion: GradleVersion?,
    ): JdkResolution.Unsupported? {
        if (gradleVersion == null) return null
        val incompatibility = GradleJdkCompatibility.check(gradleVersion, candidate) ?: return null
        return JdkResolution.Unsupported(
            "Resolved JDK $candidate is not supported by Gradle $gradleVersion " +
                "(max supported: ${incompatibility.maxSupportedJdk})",
        )
    }
}
