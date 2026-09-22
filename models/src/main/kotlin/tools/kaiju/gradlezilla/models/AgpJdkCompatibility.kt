package tools.kaiju.gradlezilla.models

object AgpJdkCompatibility {
    // Cutoffs per https://developer.android.com/build/releases/gradle-plugin#compatibility —
    // verify against the current table before relying on these for a new AGP major version.
    @Suppress("MagicNumber")
    private val minJdk: List<Pair<AgpVersion, Int>> =
        listOf(
            AgpVersion(7, 0) to 11,
            AgpVersion(8, 0) to 17,
        ).sortedBy { it.first }

    /** Returns null when [agpVersion] predates the table (no known minimum). */
    fun minimumJdk(agpVersion: AgpVersion): Int? = minJdk.lastOrNull { agpVersion >= it.first }?.second
}

data class AgpVersion(
    val major: Int,
    val minor: Int,
) : Comparable<AgpVersion> {
    override fun compareTo(other: AgpVersion): Int =
        compareValuesBy(
            this,
            other,
            AgpVersion::major,
            AgpVersion::minor,
        )

    override fun toString(): String = "$major.$minor"

    companion object {
        private val RE = Regex("""(\d+)\.(\d+)""")

        /** Parses "8.5" or "8.5.0-rc01" → 8.5. Null if unrecognisable. */
        fun parse(version: String): AgpVersion? =
            RE.find(version)?.destructured?.let { (maj, min) ->
                AgpVersion(maj.toInt(), min.toInt())
            }
    }
}
