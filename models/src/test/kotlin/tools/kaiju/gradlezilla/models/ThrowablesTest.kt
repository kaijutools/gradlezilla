package tools.kaiju.gradlezilla.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** A settable `cause` that bypasses `initCause`'s direct-self-cycle guard, for cyclic-chain tests. */
private class MutableCauseThrowable(
    message: String,
) : Throwable(message) {
    var causeOverride: Throwable? = null

    override val cause: Throwable?
        get() = causeOverride
}

class ThrowablesTest {
    @Test
    fun rootCause_returnsSelfWhenThereIsNoCause() {
        val e = RuntimeException("top")

        assertSame(e, e.rootCause())
    }

    @Test
    fun rootCause_walksToTheDeepestCause() {
        val root = IllegalStateException("root")
        val middle = RuntimeException("middle", root)
        val top = RuntimeException("top", middle)

        assertSame(root, top.rootCause())
    }

    @Test
    fun rootCause_terminatesOnACyclicCauseChain() {
        val a = MutableCauseThrowable("a")
        val b = MutableCauseThrowable("b")
        a.causeOverride = b
        b.causeOverride = a

        // Must return, not hang — which value it lands on is incidental.
        val result = a.rootCause()

        assertEquals(true, result === a || result === b)
    }

    @Test
    fun causeChain_visitsEachThrowableInACyclicChainExactlyOnce() {
        val a = MutableCauseThrowable("a")
        val b = MutableCauseThrowable("b")
        a.causeOverride = b
        b.causeOverride = a

        val chain = a.causeChain().toList()

        assertEquals(listOf(a, b), chain)
    }
}
