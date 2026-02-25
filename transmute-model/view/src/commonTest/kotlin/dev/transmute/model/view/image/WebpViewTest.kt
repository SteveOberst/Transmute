package dev.transmute.model.view.image

import dev.transmute.model.core.Bytes
import dev.transmute.model.identify.RiffChunkId
import dev.transmute.model.structure.common.RiffChunk
import dev.transmute.model.structure.image.*
import dev.transmute.model.view.StructureView
import dev.transmute.model.view.MutableStructureView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class WebpViewTest {

    // --- Helpers ---

    private fun riffChunk(
        formType: String = "WEBP",
        children: List<RiffChunk> = emptyList(),
    ) = RiffChunk(
        id = RiffChunkId("RIFF"),
        size = 4u, // minimal
        formType = RiffChunkId(formType),
        children = children,
    )

    private fun vp8Chunk(data: ByteArray = ByteArray(10)): RiffChunk {
        // VP8 frame header: 3-byte tag, 3-byte sync code (0x9D 0x01 0x2A), 2-byte width, 2-byte height
        val d = if (data.size >= 10 && data.contentEquals(ByteArray(10))) {
            ByteArray(10).also { it[3] = 0x9D.toByte(); it[4] = 0x01; it[5] = 0x2A }
        } else data
        return RiffChunk(
            id = RiffChunkId("VP8 "),
            size = d.size.toUInt(),
            data = Bytes(d),
        )
    }

    private fun minimalWebp(
        children: List<RiffChunk> = listOf(vp8Chunk()),
    ) = Webp(riff = riffChunk(children = children))

    // --- view() tests ---

    @Test
    fun viewReturnsCorrectRiff() {
        val file = minimalWebp()
        val view = file.view()
        assertEquals(file.riff, view.riff)
    }

    @Test
    fun viewReturnsChunks() {
        val file = minimalWebp()
        assertEquals(file.chunks, file.view().chunks)
    }

    @Test
    fun viewReturnsFormat() {
        val view = minimalWebp().view()
        assertEquals(WebpFormat.Lossy, view.format)
    }

    @Test
    fun viewReportsNoAnimationForSimple() {
        val view = minimalWebp().view()
        assertFalse(view.hasAnimation)
    }

    // --- edit() tests ---

    @Test
    fun editNoOpRoundTrip() {
        val original = minimalWebp()
        val edited = original.edit { }
        assertEquals(original, edited)
    }

    @Test
    fun editMutatesRiff() {
        val original = minimalWebp()
        val edited = original.edit {
            riff = riff.copy(children = emptyList())
        }
        assertEquals(0, edited.chunks.size)
    }

    // --- Type hierarchy tests ---

    @Test
    fun viewIsFileView() {
        assertIs<StructureView<Webp>>(minimalWebp().view())
    }

    @Test
    fun viewIsWebpView() {
        assertIs<WebpView>(minimalWebp().view())
    }

    @Test
    fun mutableViewIsMutableStructureView() {
        val view = MutableWebpView(minimalWebp())
        assertIs<MutableStructureView<Webp>>(view)
        assertIs<WebpView>(view)
    }

    // --- Consistency tests ---

    @Test
    fun viewAndFileReturnSameValues() {
        // Use an empty-children RIFF to avoid VP8 parsing edge cases
        val file = minimalWebp(children = emptyList())
        val view = file.view()
        assertEquals(file.riff, view.riff)
        assertEquals(file.chunks, view.chunks)
        assertEquals(file.format, view.format)
        assertEquals(file.hasAlpha, view.hasAlpha)
        assertEquals(file.hasAnimation, view.hasAnimation)
        assertEquals(file.width, view.width)
        assertEquals(file.height, view.height)
    }
}
