package tools.kaiju.gradlezilla.cli.models

data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int,
) {
    override fun toString(): String = "$major.$minor.$patch"
}
