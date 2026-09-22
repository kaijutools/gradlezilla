package tools.kaiju.gradlezilla.models

import kotlin.test.Test
import kotlin.test.assertSame

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
}
