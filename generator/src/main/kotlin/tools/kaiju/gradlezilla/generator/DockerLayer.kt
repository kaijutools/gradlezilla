package tools.kaiju.gradlezilla.generator

/**
 * A Docker layer represents a cache boundary, not an instruction type.
 * L4–L6 mix COPY + RUN in a single cache-relevant block
 */

fun interface DockerLayer {
    fun render(): String
}
