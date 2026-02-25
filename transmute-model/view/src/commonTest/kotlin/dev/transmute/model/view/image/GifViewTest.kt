package dev.transmute.model.view.image

import dev.transmute.model.core.Pixels
import dev.transmute.model.structure.image.*
import dev.transmute.model.view.StructureView
import dev.transmute.model.view.MutableStructureView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GifViewTest {

    // --- Helpers ---

    private fun minimalGif(
        version: GifVersion = GifVersion.Gif89a,
        width: UShort = 320u,
        height: UShort = 240u,
        blocks: List<GifBlock> = emptyList(),
    ) = Gif(
        version = version,
        screenDescriptor = GifLogicalScreenDescriptor(
            width = width,
            height = height,
            packed = 0u,
            backgroundColorIndex = 0u,
            pixelAspectRatio = 0u,
        ),
        blocks = blocks,
    )

    /** An image block (introducer 0x2C) with enough data for an image descriptor. */
    private fun imageBlock(
        left: Int = 0, top: Int = 0,
        width: Int = 16, height: Int = 16,
    ): GifBlock {
        val data = ByteArray(9)
        data[0] = (left and 0xFF).toByte()
        data[1] = (left shr 8).toByte()
        data[2] = (top and 0xFF).toByte()
        data[3] = (top shr 8).toByte()
        data[4] = (width and 0xFF).toByte()
        data[5] = (width shr 8).toByte()
        data[6] = (height and 0xFF).toByte()
        data[7] = (height shr 8).toByte()
        data[8] = 0 // packed
        return GifBlock(
            introducer = 0x2Cu,
            data = dev.transmute.model.core.Bytes(data),
        )
    }

    // --- view() tests ---

    @Test
    fun viewReturnsCorrectFields() {
        val file = minimalGif()
        val view = file.view()
        assertEquals(GifVersion.Gif89a, view.version)
        assertEquals(320u.toUShort(), view.screenDescriptor.width)
        assertEquals(240u.toUShort(), view.screenDescriptor.height)
        assertTrue(view.blocks.isEmpty())
    }

    @Test
    fun viewComputesDimensions() {
        val view = minimalGif(width = 640u, height = 480u).view()
        assertEquals(Pixels(640), view.width)
        assertEquals(Pixels(480), view.height)
    }

    @Test
    fun viewReportsNotAnimatedForSingleFrame() {
        val view = minimalGif(blocks = listOf(imageBlock())).view()
        assertEquals(1, view.frameCount)
        assertFalse(view.isAnimated)
    }

    @Test
    fun viewReportsAnimatedForMultipleFrames() {
        val view = minimalGif(blocks = listOf(imageBlock(), imageBlock())).view()
        assertEquals(2, view.frameCount)
        assertTrue(view.isAnimated)
    }

    // --- edit() tests ---

    @Test
    fun editNoOpRoundTrip() {
        val original = minimalGif(blocks = listOf(imageBlock()))
        val edited = original.edit { }
        assertEquals(original, edited)
    }

    @Test
    fun editMutatesVersion() {
        val original = minimalGif(version = GifVersion.Gif87a)
        val edited = original.edit { version = GifVersion.Gif89a }
        assertEquals(GifVersion.Gif89a, edited.version)
    }

    @Test
    fun editMutatesScreenDescriptor() {
        val original = minimalGif()
        val edited = original.edit {
            screenDescriptor = screenDescriptor.copy(width = 800u)
        }
        assertEquals(Pixels(800), edited.width)
    }

    @Test
    fun editAddsBlocks() {
        val original = minimalGif()
        val edited = original.edit {
            blocks = listOf(imageBlock(), imageBlock(), imageBlock())
        }
        assertEquals(3, edited.frameCount)
        assertTrue(edited.isAnimated)
    }

    // --- Type hierarchy tests ---

    @Test
    fun viewIsFileView() {
        assertIs<StructureView<Gif>>(minimalGif().view())
    }

    @Test
    fun viewIsGifView() {
        assertIs<GifView>(minimalGif().view())
    }

    @Test
    fun mutableViewIsMutableStructureView() {
        val view = MutableGifView(minimalGif())
        assertIs<MutableStructureView<Gif>>(view)
        assertIs<GifView>(view)
    }

    // --- Consistency tests ---

    @Test
    fun viewAndFileReturnSameValues() {
        val file = minimalGif(blocks = listOf(imageBlock()))
        val view = file.view()
        assertEquals(file.version, view.version)
        assertEquals(file.screenDescriptor, view.screenDescriptor)
        assertEquals(file.blocks, view.blocks)
        assertEquals(file.width, view.width)
        assertEquals(file.height, view.height)
        assertEquals(file.frameCount, view.frameCount)
        assertEquals(file.isAnimated, view.isAnimated)
    }
}
