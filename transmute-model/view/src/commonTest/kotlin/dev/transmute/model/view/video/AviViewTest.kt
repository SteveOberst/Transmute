package dev.transmute.model.view.video

import dev.transmute.model.core.Bytes
import dev.transmute.model.identify.RiffChunkId
import dev.transmute.model.structure.common.RiffChunk
import dev.transmute.model.structure.video.*
import dev.transmute.model.view.StructureView
import dev.transmute.model.view.MutableStructureView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AviViewTest {

    // --- Helpers ---

    private fun avihChunk() = RiffChunk(
        id = RiffChunkId("avih"),
        size = 56u,
        data = Bytes(ByteArray(56)),
    )

    private fun strlList() = RiffChunk(
        id = RiffChunkId("LIST"),
        size = 4u,
        formType = RiffChunkId("strl"),
        children = emptyList(),
    )

    private fun hdrlList(streamCount: Int = 1) = RiffChunk(
        id = RiffChunkId("LIST"),
        size = 4u,
        formType = RiffChunkId("hdrl"),
        children = listOf(avihChunk()) + (1..streamCount).map { strlList() },
    )

    private fun moviList() = RiffChunk(
        id = RiffChunkId("LIST"),
        size = 4u,
        formType = RiffChunkId("movi"),
        children = emptyList(),
    )

    private fun idx1Chunk() = RiffChunk(
        id = RiffChunkId("idx1"),
        size = 0u,
        data = Bytes(ByteArray(0)),
    )

    private fun minimalAvi(
        children: List<RiffChunk> = listOf(hdrlList(), moviList(), idx1Chunk()),
    ) = Avi(
        riff = RiffChunk(
            id = RiffChunkId("RIFF"),
            size = 4u,
            formType = RiffChunkId("AVI "),
            children = children,
        ),
    )

    // --- view() tests ---

    @Test
    fun viewReturnsCorrectRiff() {
        val file = minimalAvi()
        assertEquals(file.riff, file.inspect().riff)
    }

    @Test
    fun viewReturnsChunks() {
        assertEquals(3, minimalAvi().inspect().chunks.size)
    }

    @Test
    fun viewReturnsHeaderList() {
        assertNotNull(minimalAvi().inspect().headerList)
    }

    @Test
    fun viewReturnsMovieList() {
        assertNotNull(minimalAvi().inspect().movieList)
    }

    @Test
    fun viewReturnsIndexChunk() {
        assertNotNull(minimalAvi().inspect().indexChunk)
    }

    @Test
    fun viewReturnsNullHeaderWhenAbsent() {
        val view = minimalAvi(children = listOf(moviList())).inspect()
        assertNull(view.headerList)
        assertNull(view.mainHeader)
    }

    @Test
    fun viewReturnsStreamCount() {
        val view = minimalAvi(children = listOf(hdrlList(streamCount = 3), moviList())).inspect()
        assertEquals(3, view.streamCount)
    }

    @Test
    fun viewReturnsZeroStreamCountWhenNoHeader() {
        assertEquals(0, minimalAvi(children = listOf(moviList())).inspect().streamCount)
    }

    // --- edit() tests ---

    @Test
    fun editNoOpRoundTrip() {
        val original = minimalAvi()
        assertEquals(original, original.edit { })
    }

    @Test
    fun editRemovesIndex() {
        val original = minimalAvi()
        val edited = original.edit {
            riff = riff.copy(children = riff.children.filter { it.id.value != "idx1" })
        }
        assertNull(edited.inspect().indexChunk)
    }

    @Test
    fun editStripsToHeaderOnly() {
        val original = minimalAvi()
        val edited = original.edit {
            riff = riff.copy(
                children = riff.children.filter {
                    it.id.value == "LIST" && it.formType?.value == "hdrl"
                },
            )
        }
        assertNotNull(edited.inspect().headerList)
        assertNull(edited.inspect().movieList)
        assertNull(edited.inspect().indexChunk)
    }

    // --- Type hierarchy tests ---

    @Test
    fun viewIsFileView() {
        assertIs<StructureView<Avi>>(minimalAvi().inspect())
    }

    @Test
    fun viewIsAviView() {
        assertIs<AviView>(minimalAvi().inspect())
    }

    @Test
    fun mutableViewIsMutableStructureView() {
        val view = MutableAviView(minimalAvi())
        assertIs<MutableStructureView<Avi>>(view)
        assertIs<AviView>(view)
    }

    // --- Consistency tests ---

    @Test
    fun viewAndFileReturnSameValues() {
        val file = minimalAvi()
        val view = file.inspect()
        assertEquals(file.riff, view.riff)
        assertEquals(file.chunks.size, view.chunks.size)
        assertEquals(file.headerList?.formType, view.headerList?.formType)
        assertEquals(file.movieList?.formType, view.movieList?.formType)
        assertEquals(file.indexChunk?.id, view.indexChunk?.id)
        assertEquals(file.streamCount, view.streamCount)
    }
}
