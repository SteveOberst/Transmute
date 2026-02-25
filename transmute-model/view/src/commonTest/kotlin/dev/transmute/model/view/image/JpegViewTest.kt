package dev.transmute.model.view.image

import dev.transmute.model.core.Bytes
import dev.transmute.model.structure.image.*
import dev.transmute.model.view.StructureView
import dev.transmute.model.view.MutableStructureView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JpegViewTest {

    // --- Helpers ---

    private fun minimalJpeg(
        segments: List<JpegSegment> = listOf(
            JpegSegment(marker = 0xD8u), // SOI
            JpegSegment(marker = 0xD9u), // EOI
        ),
    ) = Jpeg(segments)

    /** SOF0 marker with basic dimension data. */
    private fun sofSegment(width: Int = 320, height: Int = 240): JpegSegment {
        val data = ByteArray(6)
        data[0] = 8 // precision
        data[1] = (height shr 8).toByte()
        data[2] = (height and 0xFF).toByte()
        data[3] = (width shr 8).toByte()
        data[4] = (width and 0xFF).toByte()
        data[5] = 0 // number of components
        return JpegSegment(marker = 0xC0u, data = Bytes(data))
    }

    /** COM segment with a text comment. */
    private fun commentSegment(text: String) =
        JpegSegment(marker = 0xFEu, data = Bytes(text.encodeToByteArray()))

    // --- view() tests ---

    @Test
    fun viewReturnsCorrectSegments() {
        val file = minimalJpeg()
        val view = file.view()
        assertEquals(file.segments, view.segments)
    }

    @Test
    fun viewReflectsNullSofWhenAbsent() {
        val view = minimalJpeg().view()
        assertNull(view.sofData)
        assertNull(view.jfifHeader)
        assertTrue(view.comments.isEmpty())
    }

    @Test
    fun viewReturnsSofDataWhenPresent() {
        val file = minimalJpeg(
            listOf(JpegSegment(0xD8u), sofSegment(640, 480), JpegSegment(0xD9u))
        )
        val view = file.view()
        assertEquals(640u.toUShort(), view.sofData?.width)
        assertEquals(480u.toUShort(), view.sofData?.height)
    }

    @Test
    fun viewReturnsComments() {
        val file = minimalJpeg(
            listOf(JpegSegment(0xD8u), commentSegment("hello"), JpegSegment(0xD9u))
        )
        assertEquals(listOf("hello"), file.view().comments)
    }

    // --- edit() tests ---

    @Test
    fun editNoOpRoundTrip() {
        val original = minimalJpeg()
        val edited = original.edit { }
        assertEquals(original.segments, edited.segments)
    }

    @Test
    fun editMutatesSegments() {
        val original = minimalJpeg(
            listOf(JpegSegment(0xD8u), commentSegment("old"), JpegSegment(0xD9u))
        )
        val edited = original.edit {
            segments = segments.map {
                if (it.marker == 0xFEu.toUByte()) commentSegment("new") else it
            }
        }
        assertEquals(listOf("new"), edited.comments)
    }

    @Test
    fun editRemovesCommentSegments() {
        val original = minimalJpeg(
            listOf(JpegSegment(0xD8u), commentSegment("x"), JpegSegment(0xD9u))
        )
        val edited = original.edit {
            segments = segments.filter { it.marker != 0xFEu.toUByte() }
        }
        assertTrue(edited.comments.isEmpty())
    }

    @Test
    fun editComputedPropertiesReflectMutations() {
        val original = minimalJpeg()
        val edited = original.edit {
            segments = listOf(JpegSegment(0xD8u), sofSegment(1920, 1080), JpegSegment(0xD9u))
        }
        assertEquals(1920u.toUShort(), edited.sofData?.width)
        assertEquals(1080u.toUShort(), edited.sofData?.height)
    }

    // --- Type hierarchy tests ---

    @Test
    fun viewIsFileView() {
        assertIs<StructureView<Jpeg>>(minimalJpeg().view())
    }

    @Test
    fun viewIsJpegView() {
        assertIs<JpegView>(minimalJpeg().view())
    }

    @Test
    fun mutableViewIsMutableStructureView() {
        val view = MutableJpegView(minimalJpeg())
        assertIs<MutableStructureView<Jpeg>>(view)
        assertIs<JpegView>(view)
    }

    // --- Consistency tests ---

    @Test
    fun viewAndFileReturnSameValues() {
        val file = minimalJpeg(
            listOf(JpegSegment(0xD8u), sofSegment(), commentSegment("test"), JpegSegment(0xD9u))
        )
        val view = file.view()
        assertEquals(file.segments, view.segments)
        assertEquals(file.sofData, view.sofData)
        assertEquals(file.jfifHeader, view.jfifHeader)
        assertEquals(file.comments, view.comments)
    }
}
