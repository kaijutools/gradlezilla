package tools.kaiju.gradlezilla.models

/**
 * The deepest cause in this exception's chain — e.g. unwraps a Tooling API
 * `GradleConnectionException`'s generic "Could not execute build ..." wrapper down to the actual
 * reason the build failed, which is what a caller actually wants surfaced to the user.
 */
fun Throwable.rootCause(): Throwable = generateSequence(this) { it.cause }.last()
