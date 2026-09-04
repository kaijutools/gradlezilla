package tools.kaiju.gradlezilla.models

object GradleJdkCompatibility {
    private val maxJdk: List<Pair<GradleVersion, Int>> =
        listOf(
            GradleVersion(5, 0) to 11,
            GradleVersion(5, 4) to 12,
            GradleVersion(6, 0) to 13,
            GradleVersion(6, 3) to 14,
            GradleVersion(6, 7) to 15,
            GradleVersion(7, 0) to 16,
            GradleVersion(7, 3) to 17,
            GradleVersion(7, 5) to 18,
            GradleVersion(7, 6) to 19,
            GradleVersion(8, 0) to 19,
            GradleVersion(8, 3) to 20,
            GradleVersion(8, 5) to 21,
            GradleVersion(8, 8) to 22,
            GradleVersion(8, 10) to 23,
            GradleVersion(8, 14) to 24,
        ).sortedBy { it.first }

    /** Returns null when compatible, unknown, or newer than the table. */
    fun check(
        gradle: GradleVersion,
        runningJdk: Int,
    ): Incompatibility? {
        // Fail open: a Gradle newer than anything we know about is assumed fine.
        if (gradle >= maxJdk.last().first) return null
        val max = maxJdk.lastOrNull { gradle >= it.first }?.second ?: return null
        return if (runningJdk > max) Incompatibility(gradle, runningJdk, max) else null
    }

    data class Incompatibility(
        val gradleVersion: GradleVersion,
        val runningJdk: Int,
        val maxSupportedJdk: Int,
    )
}

data class GradleVersion(
    val major: Int,
    val minor: Int,
) : Comparable<GradleVersion> {
    override fun compareTo(other: GradleVersion): Int = compareValuesBy(this, other, GradleVersion::major, GradleVersion::minor)

    override fun toString(): String = "$major.$minor"

    companion object {
        private val RE = Regex("""gradle-(\d+)\.(\d+)""")

        /** Parses "…/gradle-8.0.2-all.zip" → 8.0. Null if unrecognisable. */
        fun fromDistributionUrl(url: String): GradleVersion? =
            RE.find(url)?.destructured?.let { (maj, min) ->
                GradleVersion(maj.toInt(), min.toInt())
            }
    }
}
