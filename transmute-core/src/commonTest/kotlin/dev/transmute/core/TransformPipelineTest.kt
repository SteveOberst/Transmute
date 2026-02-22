package dev.transmute.core

import dev.transmute.core.pipeline.Transform
import dev.transmute.core.pipeline.TransformId
import dev.transmute.core.pipeline.TransformPipeline
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class TransformPipelineTest {

    // -- Test transform types --

    private open class AlphaTransform(override val id: TransformId = TransformId("alpha")) :
        Transform<String> {
        override suspend fun apply(ir: String, context: TransmuteContext) = "$ir+alpha"
    }

    private open class BetaTransform(override val id: TransformId = TransformId("beta")) :
        Transform<String> {
        override suspend fun apply(ir: String, context: TransmuteContext) = "$ir+beta"
    }

    private open class GammaTransform(override val id: TransformId = TransformId("gamma")) :
        Transform<String> {
        override suspend fun apply(ir: String, context: TransmuteContext) = "$ir+gamma"
    }

    private fun pipeline() = TransformPipeline<String>()

    // -- add / addLast / addFirst --

    @Test
    fun addAppendsToEnd() {
        val p = pipeline()
        p.add(AlphaTransform())
        p.add(BetaTransform())
        assertEquals(2, p.size)
        assertIs<AlphaTransform>(p.transforms[0])
        assertIs<BetaTransform>(p.transforms[1])
    }

    @Test
    fun addLastIsAliasForAdd() {
        val p = pipeline()
        p.addLast(AlphaTransform())
        p.addLast(BetaTransform())
        assertEquals(2, p.size)
        assertIs<AlphaTransform>(p.transforms[0])
        assertIs<BetaTransform>(p.transforms[1])
    }

    @Test
    fun addFirstPrepends() {
        val p = pipeline()
        p.add(BetaTransform())
        p.addFirst(AlphaTransform())
        assertEquals(2, p.size)
        assertIs<AlphaTransform>(p.transforms[0])
        assertIs<BetaTransform>(p.transforms[1])
    }

    @Test
    fun addAllAppendsMultiple() {
        val p = pipeline()
        p.add(AlphaTransform())
        p.addAll(listOf(BetaTransform(), GammaTransform()))
        assertEquals(3, p.size)
        assertEquals("alpha", p.transforms[0].id.value)
        assertEquals("beta", p.transforms[1].id.value)
        assertEquals("gamma", p.transforms[2].id.value)
    }

    // -- before / after --

    @Test
    fun beforeInsertsBeforeTarget() {
        val p = pipeline()
        p.add(AlphaTransform())
        p.add(GammaTransform())
        p.before<GammaTransform>(BetaTransform())
        assertEquals(3, p.size)
        assertEquals("alpha", p.transforms[0].id.value)
        assertEquals("beta", p.transforms[1].id.value)
        assertEquals("gamma", p.transforms[2].id.value)
    }

    @Test
    fun beforeThrowsWhenTargetMissing() {
        val p = pipeline()
        p.add(AlphaTransform())
        assertFailsWith<IllegalArgumentException> {
            p.before<GammaTransform>(BetaTransform())
        }
    }

    @Test
    fun afterInsertsAfterTarget() {
        val p = pipeline()
        p.add(AlphaTransform())
        p.add(GammaTransform())
        p.after<AlphaTransform>(BetaTransform())
        assertEquals(3, p.size)
        assertEquals("alpha", p.transforms[0].id.value)
        assertEquals("beta", p.transforms[1].id.value)
        assertEquals("gamma", p.transforms[2].id.value)
    }

    @Test
    fun afterThrowsWhenTargetMissing() {
        val p = pipeline()
        p.add(AlphaTransform())
        assertFailsWith<IllegalArgumentException> {
            p.after<GammaTransform>(BetaTransform())
        }
    }

    // -- remove --

    @Test
    fun removeByTypeReturnsTrueAndRemoves() {
        val p = pipeline()
        p.add(AlphaTransform())
        p.add(BetaTransform())
        assertTrue(p.remove<AlphaTransform>())
        assertEquals(1, p.size)
        assertIs<BetaTransform>(p.transforms[0])
    }

    @Test
    fun removeByTypeReturnsFalseWhenMissing() {
        val p = pipeline()
        p.add(AlphaTransform())
        assertFalse(p.remove<GammaTransform>())
        assertEquals(1, p.size)
    }

    @Test
    fun removeByInstanceReturnsTrueAndRemoves() {
        val p = pipeline()
        val alpha = AlphaTransform()
        p.add(alpha)
        p.add(BetaTransform())
        assertTrue(p.remove(alpha))
        assertEquals(1, p.size)
    }

    // -- replace --

    @Test
    fun replaceSwapsFirstOccurrence() {
        val p = pipeline()
        p.add(AlphaTransform())
        p.add(BetaTransform())
        val newAlpha = GammaTransform(TransformId("alpha-v2"))
        p.replace<AlphaTransform>(newAlpha)
        assertEquals(2, p.size)
        assertEquals("alpha-v2", p.transforms[0].id.value)
        assertIs<BetaTransform>(p.transforms[1])
    }

    @Test
    fun replaceThrowsWhenTargetMissing() {
        val p = pipeline()
        p.add(AlphaTransform())
        assertFailsWith<IllegalArgumentException> {
            p.replace<GammaTransform>(BetaTransform())
        }
    }

    // -- has / get --

    @Test
    fun hasReturnsTrueWhenPresent() {
        val p = pipeline()
        p.add(AlphaTransform())
        assertTrue(p.has<AlphaTransform>())
    }

    @Test
    fun hasReturnsFalseWhenMissing() {
        val p = pipeline()
        p.add(AlphaTransform())
        assertFalse(p.has<GammaTransform>())
    }

    @Test
    fun getReturnsInstanceWhenPresent() {
        val p = pipeline()
        val alpha = AlphaTransform()
        p.add(alpha)
        assertSame(alpha, p.get<AlphaTransform>())
    }

    @Test
    fun getReturnsNullWhenMissing() {
        val p = pipeline()
        p.add(AlphaTransform())
        assertNull(p.get<GammaTransform>())
    }

    // -- clear / isEmpty / size --

    @Test
    fun clearRemovesAll() {
        val p = pipeline()
        p.add(AlphaTransform())
        p.add(BetaTransform())
        p.clear()
        assertEquals(0, p.size)
        assertTrue(p.isEmpty)
    }

    @Test
    fun isEmptyOnNewPipeline() {
        assertTrue(pipeline().isEmpty)
    }

    @Test
    fun isEmptyFalseAfterAdd() {
        val p = pipeline()
        p.add(AlphaTransform())
        assertFalse(p.isEmpty)
    }

    // -- operators --

    @Test
    fun plusAssignOperatorAdds() {
        val p = pipeline()
        p += AlphaTransform()
        assertEquals(1, p.size)
        assertIs<AlphaTransform>(p.transforms[0])
    }

    @Test
    fun iteratorYieldsInOrder() {
        val p = pipeline()
        p.add(AlphaTransform())
        p.add(BetaTransform())
        p.add(GammaTransform())
        val ids = p.iterator().asSequence().map { it.id.value }.toList()
        assertEquals(listOf("alpha", "beta", "gamma"), ids)
    }

    // -- toString --

    @Test
    fun toStringShowsTransformIds() {
        val p = pipeline()
        p.add(AlphaTransform())
        p.add(BetaTransform())
        assertEquals("TransformPipeline(alpha, beta)", p.toString())
    }

    // -- execution order --

    @Test
    fun transformsExecuteInPipelineOrder() = runTest {
        val p = pipeline()
        p.add(AlphaTransform())
        p.add(BetaTransform())
        p.add(GammaTransform())

        var result = "start"
        for (t in p) {
            result = t.apply(result, TransmuteContext(logger = TransmuteLogger.Noop))
        }
        assertEquals("start+alpha+beta+gamma", result)
    }

    // -- fluent chaining --

    @Test
    fun fluentChainingAllowsMethodChain() {
        val p = pipeline()
            .add(AlphaTransform())
            .add(BetaTransform())
            .addFirst(GammaTransform())

        assertEquals(3, p.size)
        assertEquals("gamma", p.transforms[0].id.value)
        assertEquals("alpha", p.transforms[1].id.value)
        assertEquals("beta", p.transforms[2].id.value)
    }
}
