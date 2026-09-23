package tools.kaiju.gradlezilla.models

import java.util.Collections
import java.util.IdentityHashMap

/**
 * This throwable's cause chain, starting with itself. `Throwable.cause` chains are conventionally
 * acyclic (`initCause` refuses a direct self-cycle), but nothing stops a custom `Throwable`
 * subclass overriding `getCause()` from forming a longer cycle — so this stops the moment it
 * would revisit a throwable already seen, rather than iterating forever.
 */
fun Throwable.causeChain(): Sequence<Throwable> =
    sequence {
        val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        var current: Throwable? = this@causeChain
        while (current != null && visited.add(current)) {
            yield(current)
            current = current.cause
        }
    }

/**
 * The deepest cause in this exception's chain — e.g. unwraps a Tooling API
 * `GradleConnectionException`'s generic "Could not execute build ..." wrapper down to the actual
 * reason the build failed, which is what a caller actually wants surfaced to the user.
 */
fun Throwable.rootCause(): Throwable = causeChain().last()
