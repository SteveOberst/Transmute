package dev.transmute.model.view.image

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
import dev.transmute.model.identify.FourCC
import dev.transmute.model.structure.image.*
import dev.transmute.model.view.ByteArrayChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Helpers — self-contained, mirrors MutablePngViewTest
// ---------------------------------------------------------------------------

private fun UInt.be(): ByteArray = byteArrayOf(
    (this shr 24).toByte(), (this shr 16).toByte(),
    (this shr 8).toByte(), this.toByte(),
)

@Suppress("unused")
private fun UShort.be(): ByteArray = byteArrayOf(
    (this.toInt() shr 8).toByte(), this.toByte(),
)

private fun buildTestChunk(type: String, data: ByteArray = ByteArray(0)): PngChunk {
    val typeBytes = type.encodeToByteArray()
    return PngChunk(
        length = data.size.toUInt(),
        type = FourCC(type),
        data = data.asBytes(),
        crc = crc32(typeBytes + data),
    )
}

private fun ihdrData(
    width: UInt = 8u,
    height: UInt = 8u,
    bitDepth: UByte = 8u,
    colorType: Int = 2,
    compressionMethod: Int = 0,
    filterMethod: Int = 0,
    interlaceMethod: Int = 0,
): ByteArray {
    val out = ByteArray(13)
    width.be().copyInto(out, 0)
    height.be().copyInto(out, 4)
    out[8] = bitDepth.toByte()
    out[9] = colorType.toByte()
    out[10] = compressionMethod.toByte()
    out[11] = filterMethod.toByte()
    out[12] = interlaceMethod.toByte()
    return out
}

private fun minimalPng(
    width: UInt = 8u,
    height: UInt = 8u,
    colorType: Int = 2,
    extraChunks: List<PngChunk> = emptyList(),
): Png {
    val ihdr = buildTestChunk("IHDR", ihdrData(width, height, colorType = colorType))
    val iend = buildTestChunk("IEND")
    return Png(
        signature = Bytes(Png.SIGNATURE.copyOf()),
        chunks = listOf(ihdr) + extraChunks + iend,
    )
}

/** Serialize a [Png] to bytes — used to seed channels. */
private fun Png.rawBytes(): ByteArray = toBytes().data

/** Parse the IHDR width from raw PNG bytes (offset 16..19, first chunk data). */
private fun readIhdrWidthFromBytes(bytes: ByteArray): UInt {
    // signature (8) + chunk-length (4) + chunk-type (4) = offset 16
    val o = 16
    return ((bytes[o].toUInt() and 0xFFu) shl 24) or
            ((bytes[o + 1].toUInt() and 0xFFu) shl 16) or
            ((bytes[o + 2].toUInt() and 0xFFu) shl 8) or
            (bytes[o + 3].toUInt() and 0xFFu)
}

/** Parse the IHDR height from raw PNG bytes (offset 20..23). */
private fun readIhdrHeightFromBytes(bytes: ByteArray): UInt {
    val o = 20
    return ((bytes[o].toUInt() and 0xFFu) shl 24) or
            ((bytes[o + 1].toUInt() and 0xFFu) shl 16) or
            ((bytes[o + 2].toUInt() and 0xFFu) shl 8) or
            (bytes[o + 3].toUInt() and 0xFFu)
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

class StreamingPngViewTest {

    // === No-op: zero I/O ===

    @Test
    fun noOpEditStreamingProducesIdenticalBytes() = runTest {
        val png = minimalPng(width = 16u, height = 32u)
        val original = png.rawBytes()
        val result = png.editStreaming {}
        assertContentEquals(original, result)
    }

    @Test
    fun noOpEditStreamingDoesNotModifyChannel() = runTest {
        val png = minimalPng()
        val original = png.rawBytes()
        val channel = ByteArrayChannel(original.copyOf())

        png.editStreaming(channel) { /* no mutations */ }

        assertContentEquals(original, channel.toByteArray())
    }

    // === Same-size edit (in-place fast path) ===

    @Test
    fun editIhdrWidthInPlace() = runTest {
        val png = minimalPng(width = 8u, height = 8u)
        val original = png.rawBytes()
        val channel = ByteArrayChannel(original.copyOf())

        png.editStreaming(channel) {
            ihdr = ihdr.copy(width = 1920u)
        }

        val patched = channel.toByteArray()

        // File size unchanged (same-size edit)
        assertEquals(original.size, patched.size)

        // IHDR width changed
        assertEquals(1920u, readIhdrWidthFromBytes(patched))

        // IHDR height unchanged
        assertEquals(8u, readIhdrHeightFromBytes(patched))

        // Signature unchanged
        assertContentEquals(Png.SIGNATURE, patched.sliceArray(0..7))
    }

    @Test
    fun editIhdrHeightAndBitDepthInPlace() = runTest {
        val png = minimalPng(width = 8u, height = 8u)
        val result = png.editStreaming {
            ihdr = ihdr.copy(height = 1080u, bitDepth = 16u)
        }

        assertEquals(1080u, readIhdrHeightFromBytes(result))

        // Verify by re-parsing: build a Png from the result via build()
        val patchedPng = png.editStreaming {
            ihdr = ihdr.copy(height = 1080u, bitDepth = 16u)
        }
        // The byte at IHDR data offset+8 should be 16 (bitDepth)
        // offset 16 = first byte of IHDR data (width)
        // offset 24 = bitDepth
        assertEquals(16.toByte(), patchedPng[24])
    }

    @Test
    fun inPlaceEditPreservesFileSizeExactly() = runTest {
        val gamaData = 45455u.be()
        val png = minimalPng(extraChunks = listOf(buildTestChunk("gAMA", gamaData)))
        val original = png.rawBytes()
        val channel = ByteArrayChannel(original.copyOf())

        // Change gAMA value (same 4-byte size)
        png.editStreaming(channel) {
            gama = PngGama(100000u) // 1.0 gamma
        }

        val patched = channel.toByteArray()
        assertEquals(original.size, patched.size, "Same-size edit should not change file size")
    }

    @Test
    fun inPlaceEditProducesCorrectCrc() = runTest {
        val png = minimalPng(width = 8u)
        val result = png.editStreaming {
            ihdr = ihdr.copy(width = 256u)
        }

        // Re-parse the IHDR chunk: offset 8 is start of IHDR chunk
        // 4 bytes length + 4 bytes type + 13 bytes data + 4 bytes CRC
        val ihdrStart = 8
        val ihdrTypeBytes = result.sliceArray(ihdrStart + 4..ihdrStart + 7)
        val ihdrDataBytes = result.sliceArray(ihdrStart + 8..ihdrStart + 20)
        val ihdrCrcBytes = result.sliceArray(ihdrStart + 21..ihdrStart + 24)

        val expectedCrc = crc32(ihdrTypeBytes + ihdrDataBytes)
        val actualCrc = ((ihdrCrcBytes[0].toUInt() and 0xFFu) shl 24) or
                ((ihdrCrcBytes[1].toUInt() and 0xFFu) shl 16) or
                ((ihdrCrcBytes[2].toUInt() and 0xFFu) shl 8) or
                (ihdrCrcBytes[3].toUInt() and 0xFFu)

        assertEquals(expectedCrc, actualCrc, "CRC after in-place edit must be correct")
    }

    @Test
    fun inPlaceEditOnlyChangesTargetBytes() = runTest {
        val idatPayload = byteArrayOf(0x78, 0x01, 0x63, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01)
        val png = minimalPng(extraChunks = listOf(buildTestChunk("IDAT", idatPayload)))
        val original = png.rawBytes()
        val channel = ByteArrayChannel(original.copyOf())

        png.editStreaming(channel) {
            ihdr = ihdr.copy(width = 999u)
        }

        val patched = channel.toByteArray()

        // Signature bytes unchanged
        assertContentEquals(original.sliceArray(0..7), patched.sliceArray(0..7))

        // IDAT chunk and IEND chunk bytes unchanged
        // IHDR chunk: bytes 8..8+4+4+13+4-1 = 8..32
        // IDAT starts after IHDR at offset 33 (8 sig + 25 ihdr_chunk)
        val ihdrChunkSize = 4 + 4 + 13 + 4 // = 25
        val afterIhdr = 8 + ihdrChunkSize
        assertContentEquals(
            original.sliceArray(afterIhdr until original.size),
            patched.sliceArray(afterIhdr until patched.size),
            "Bytes after IHDR chunk must be unchanged for same-size edit"
        )
    }

    // === Size-changing edits (slow path: tail rewrite) ===

    @Test
    fun addGamaChunkChangesFileSize() = runTest {
        val png = minimalPng()
        assertNull(png.gama)
        val original = png.rawBytes()

        val result = png.editStreaming {
            gama = PngGama(45455u)
        }

        // gAMA chunk adds: 4 (length) + 4 (type) + 4 (data) + 4 (CRC) = 16 bytes
        assertTrue(result.size > original.size, "Adding a chunk must increase file size")

        // Verify the result is a valid PNG by checking signature and structure
        assertContentEquals(Png.SIGNATURE, result.sliceArray(0..7))

        // Verify IHDR is still first chunk (offset 8)
        val ihdrType = result.sliceArray(12..15)
        assertContentEquals("IHDR".encodeToByteArray(), ihdrType)

        // Verify IEND is still last chunk (last 12 bytes: 4 len=0 + 4 type + 4 CRC)
        val iendType = result.sliceArray(result.size - 8..result.size - 5)
        assertContentEquals("IEND".encodeToByteArray(), iendType)
    }

    @Test
    fun removeGamaChunkShrinks() = runTest {
        val gamaData = 45455u.be()
        val png = minimalPng(extraChunks = listOf(buildTestChunk("gAMA", gamaData)))
        assertNotNull(png.gama)
        val original = png.rawBytes()

        val result = png.editStreaming {
            gama = null
        }

        // gAMA chunk removed: file should shrink by 16 bytes
        assertEquals(original.size - 16, result.size, "Removing gAMA chunk should shrink file by 16 bytes")

        // Verify IHDR present, IEND present
        assertContentEquals(Png.SIGNATURE, result.sliceArray(0..7))
        val iendType = result.sliceArray(result.size - 8..result.size - 5)
        assertContentEquals("IEND".encodeToByteArray(), iendType)
    }

    @Test
    fun addTextChunks() = runTest {
        val png = minimalPng()
        val original = png.rawBytes()

        val result = png.editStreaming {
            textChunks = listOf(
                PngTextChunk("Author", "Transmute"),
                PngTextChunk("Comment", "Test"),
            )
        }

        assertTrue(result.size > original.size, "Adding text chunks must increase file size")

        // Parse the result to verify text chunks exist
        // We can verify by searching for "Author" bytes in the output
        val authorBytes = "Author".encodeToByteArray()
        var found = false
        for (i in 0..result.size - authorBytes.size) {
            if (result.sliceArray(i until i + authorBytes.size).contentEquals(authorBytes)) {
                found = true
                break
            }
        }
        assertTrue(found, "Result should contain 'Author' text chunk")
    }

    @Test
    fun clearTextChunks() = runTest {
        val textData = "Title".encodeToByteArray() + byteArrayOf(0) + "Test".encodeToByteArray()
        val png = minimalPng(extraChunks = listOf(buildTestChunk("tEXt", textData)))
        assertEquals(1, png.textChunks.size)
        val original = png.rawBytes()

        val result = png.editStreaming {
            textChunks = emptyList()
        }

        assertTrue(result.size < original.size, "Clearing text chunks should shrink file")
    }

    @Test
    fun replaceIdatData() = runTest {
        val idatPayload = byteArrayOf(0x78, 0x01, 0x63, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01)
        val png = minimalPng(extraChunks = listOf(buildTestChunk("IDAT", idatPayload)))

        val newPayload = byteArrayOf(0x78, 0x9C.toByte(), 0x63, 0x60, 0x00, 0x00, 0x00, 0x02, 0x00, 0x01)

        val result = png.editStreaming {
            idatChunks = listOf(PngIdat(newPayload.asBytes()))
        }

        // Check that newPayload bytes appear in the result
        var found = false
        for (i in 0..result.size - newPayload.size) {
            if (result.sliceArray(i until i + newPayload.size).contentEquals(newPayload)) {
                found = true
                break
            }
        }
        assertTrue(found, "Result should contain the new IDAT payload")
    }

    // === Consistency with MutablePngView.edit {} ===

    @Test
    fun streamingEditMatchesMutableEditForIhdr() = runTest {
        val png = minimalPng(width = 8u, height = 8u)

        val mutableResult = png.edit {
            ihdr = ihdr.copy(width = 1920u, height = 1080u)
        }

        val streamingResult = png.editStreaming {
            ihdr = ihdr.copy(width = 1920u, height = 1080u)
        }

        // Both should produce the same IHDR values
        assertEquals(1920u, readIhdrWidthFromBytes(streamingResult))
        assertEquals(1080u, readIhdrHeightFromBytes(streamingResult))
        assertEquals(mutableResult.ihdr.width, readIhdrWidthFromBytes(streamingResult))
        assertEquals(mutableResult.ihdr.height, readIhdrHeightFromBytes(streamingResult))
    }

    @Test
    fun streamingEditMatchesMutableEditForGamaAdd() = runTest {
        val png = minimalPng()

        val mutableResult = png.edit {
            gama = PngGama(45455u)
        }

        val streamingBytes = png.editStreaming {
            gama = PngGama(45455u)
        }

        // Both produce same-size output since they're adding the same chunk
        val mutableBytes = mutableResult.toBytes().data
        assertEquals(mutableBytes.size, streamingBytes.size,
            "Streaming and mutable edit should produce same file size")
    }

    // === Channel variant vs ByteArray convenience ===

    @Test
    fun channelVariantMatchesByteArrayVariant() = runTest {
        val png = minimalPng(width = 8u)

        val byteArrayResult = png.editStreaming {
            ihdr = ihdr.copy(width = 42u)
        }

        val channel = ByteArrayChannel(png.rawBytes())
        png.editStreaming(channel) {
            ihdr = ihdr.copy(width = 42u)
        }
        val channelResult = channel.toByteArray()

        assertContentEquals(byteArrayResult, channelResult)
    }

    // === Multiple chunks with same type (IDAT dedup) ===

    @Test
    fun multipleIdatChunksPreservedInNoOp() = runTest {
        val idat1 = byteArrayOf(0x78, 0x01)
        val idat2 = byteArrayOf(0x63, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01)
        val png = minimalPng(
            extraChunks = listOf(
                buildTestChunk("IDAT", idat1),
                buildTestChunk("IDAT", idat2),
            )
        )
        val original = png.rawBytes()

        val result = png.editStreaming {}
        assertContentEquals(original, result, "No-op with multiple IDAT should preserve all bytes")
    }

    // === Unknown chunks preserved ===

    @Test
    fun unknownChunksPreservedInNoOp() = runTest {
        val customData = byteArrayOf(1, 2, 3, 4)
        val png = minimalPng(extraChunks = listOf(buildTestChunk("xYzZ", customData)))
        val original = png.rawBytes()

        val result = png.editStreaming {}
        assertContentEquals(original, result, "No-op should preserve unknown chunks")
    }

    @Test
    fun unknownChunksPreservedWhenEditingIhdr() = runTest {
        val customData = byteArrayOf(1, 2, 3, 4)
        val png = minimalPng(extraChunks = listOf(buildTestChunk("xYzZ", customData)))

        val result = png.editStreaming {
            ihdr = ihdr.copy(width = 64u)
        }

        // Search for the custom data in the result
        var found = false
        for (i in 0..result.size - customData.size) {
            if (result.sliceArray(i until i + customData.size).contentEquals(customData)) {
                found = true
                break
            }
        }
        assertTrue(found, "Unknown chunk data should be preserved after in-place edit")
    }

    // === Chained streaming edits ===

    @Test
    fun chainedStreamingEdits() = runTest {
        val png = minimalPng(width = 8u, height = 8u)

        // First edit: change width
        val step1Bytes = png.editStreaming {
            ihdr = ihdr.copy(width = 100u)
        }
        assertEquals(100u, readIhdrWidthFromBytes(step1Bytes))

        // Build a Png from step1 to chain
        // Use the build() approach via edit to get updated Png
        val step1Png = png.edit { ihdr = ihdr.copy(width = 100u) }

        // Second edit: change height
        val step2Bytes = step1Png.editStreaming {
            ihdr = ihdr.copy(height = 200u)
        }

        assertEquals(100u, readIhdrWidthFromBytes(step2Bytes))
        assertEquals(200u, readIhdrHeightFromBytes(step2Bytes))
    }

    // === build() delegates correctly ===

    @Test
    fun buildViaStreamingViewMatchesMutablePngView() = runTest {
        val gamaData = 45455u.be()
        val png = minimalPng(extraChunks = listOf(buildTestChunk("gAMA", gamaData)))

        val channel = ByteArrayChannel(png.rawBytes())
        val view = StreamingPngView(png, channel)
        view.ihdr = view.ihdr.copy(width = 320u)
        view.gama = PngGama(100000u)

        val built = view.build()
        assertEquals(320u, built.ihdr.width)
        assertNotNull(built.gama)
        assertEquals(100000u, built.gama!!.gamma)
    }

    // === Edge case: edit that sets property to its original value ===

    @Test
    fun settingPropertyToSameValueIsNoOp() = runTest {
        val png = minimalPng(width = 8u)
        val original = png.rawBytes()

        val result = png.editStreaming {
            ihdr = ihdr.copy(width = 8u) // same value
        }

        assertContentEquals(original, result, "Setting same value should produce identical bytes")
    }

    // === APNG: acTL ===

    @Test
    fun addActlViaStreaming() = runTest {
        val png = minimalPng()
        val original = png.rawBytes()

        val result = png.editStreaming {
            actl = PngActl(numFrames = 10u, numPlays = 0u)
        }

        assertTrue(result.size > original.size, "Adding acTL should increase file size")

        // Verify acTL type tag is present
        val actlTag = "acTL".encodeToByteArray()
        var found = false
        for (i in 0..result.size - actlTag.size) {
            if (result.sliceArray(i until i + actlTag.size).contentEquals(actlTag)) {
                found = true
                break
            }
        }
        assertTrue(found, "Result should contain acTL chunk")
    }
}
