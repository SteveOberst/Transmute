@file:Suppress("unused")

package dev.transmute.model.structure.image

import dev.transmute.model.core.BinarySerializable
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.Pixels
import dev.transmute.model.core.asBytes
import dev.transmute.model.core.RawMediaStructure
import kotlinx.serialization.Serializable

// --- Helpers - little-endian encoding ---

private fun UShort.toLittleEndianBytes(): ByteArray = byteArrayOf(
    this.toByte(),
    (this.toInt() shr 8).toByte(),
)

// --- GIF version ---

/**
 * GIF specification version.
 */
@Serializable
enum class GifVersion(val signature: String) {
    /** GIF87a - original specification (no extensions). */
    Gif87a("GIF87a"),
    /** GIF89a - adds extensions (graphic control, application, comment). */
    Gif89a("GIF89a");

    companion object {
        fun fromSignature(sig: String): GifVersion? = entries.find { it.signature == sig }
    }
}

// --- Logical Screen Descriptor (7 bytes) ---

/**
 * The 7-byte Logical Screen Descriptor that immediately follows the
 * GIF header signature.
 *
 * ```
 * | width (2 B LE) | height (2 B LE) | packed (1 B) | bgColorIndex (1 B) | pixelAspectRatio (1 B) |
 * ```
 *
 * Packed byte layout:
 * - bit 7: Global Color Table flag
 * - bits 4-6: colour resolution (bits per primary colour minus 1)
 * - bit 3: sort flag
 * - bits 0-2: size of Global Color Table (2^(N+1) entries)
 */
@Serializable
data class GifLogicalScreenDescriptor(
    val width: UShort,
    val height: UShort,
    val packed: UByte,
    val backgroundColorIndex: UByte,
    val pixelAspectRatio: UByte,
) : BinarySerializable {

    /** `true` when a Global Color Table follows this descriptor. */
    val hasGlobalColorTable: Boolean get() = (packed.toInt() and 0x80) != 0

    /** Colour resolution: bits per primary colour (1-8). */
    val colorResolution: Int get() = ((packed.toInt() shr 4) and 0x07) + 1

    /** Whether the Global Color Table is sorted by frequency. */
    val isSorted: Boolean get() = (packed.toInt() and 0x08) != 0

    /** Number of entries in the Global Color Table (0 when absent). */
    val globalColorTableSize: Int
        get() = if (hasGlobalColorTable) 1 shl ((packed.toInt() and 0x07) + 1) else 0

    override fun toBytes(): Bytes {
        val out = ByteArray(SIZE)
        width.toLittleEndianBytes().copyInto(out, 0)
        height.toLittleEndianBytes().copyInto(out, 2)
        out[4] = packed.toByte()
        out[5] = backgroundColorIndex.toByte()
        out[6] = pixelAspectRatio.toByte()
        return out.asBytes()
    }

    companion object {
        const val SIZE = 7
    }
}

// --- Colour table entry (3 bytes) ---

/**
 * A single GIF colour-table entry (RGB, 3 bytes).
 */
@Serializable
data class GifColor(
    val red: UByte,
    val green: UByte,
    val blue: UByte,
) : BinarySerializable {
    override fun toBytes(): Bytes =
        byteArrayOf(red.toByte(), green.toByte(), blue.toByte()).asBytes()
}

// --- GIF block (the fundamental structural unit) ---

/**
 * A single GIF data block as it appears on disk.
 *
 * GIF files are a sequence of blocks after the header and optional
 * Global Color Table.  Blocks are introduced by a single byte:
 *
 * - `0x2C` - Image Descriptor (followed by optional Local Color Table,
 *   LZW minimum code size byte, and sub-block-encoded image data)
 * - `0x21` - Extension Introducer (followed by a label byte and
 *   sub-block-encoded extension data)
 * - `0x3B` - Trailer (end of file)
 *
 * The [data] field contains the **complete** raw bytes of the block
 * starting immediately **after** the introducer byte.  For image blocks
 * this includes the 9-byte image descriptor, optional local colour
 * table, LZW min code size, and all sub-blocks down to the
 * zero-length terminator.  For extension blocks this includes the
 * label byte and all sub-blocks.
 */
@Serializable
data class GifBlock(
    /** Block introducer byte: `0x2C` (image), `0x21` (extension), or `0x3B` (trailer). */
    val introducer: UByte,
    /** Complete raw block payload (see class KDoc for layout). */
    val data: Bytes = Bytes(ByteArray(0)),
) : BinarySerializable {

    override fun toBytes(): Bytes {
        val out = ByteArray(1 + data.size)
        out[0] = introducer.toByte()
        data.data.copyInto(out, 1)
        return out.asBytes()
    }
}

// --- Typed models for well-known block contents ---

/**
 * Parsed GIF Image Descriptor (9 bytes inside an image block).
 */
@Serializable
data class GifImageDescriptor(
    val left: UShort,
    val top: UShort,
    val width: UShort,
    val height: UShort,
    val packed: UByte,
) {
    val hasLocalColorTable: Boolean get() = (packed.toInt() and 0x80) != 0
    val isInterlaced: Boolean get() = (packed.toInt() and 0x40) != 0
    val isSorted: Boolean get() = (packed.toInt() and 0x20) != 0
    val localColorTableSize: Int
        get() = if (hasLocalColorTable) 1 shl ((packed.toInt() and 0x07) + 1) else 0
}

/**
 * GIF disposal method (from Graphic Control Extension).
 */
@Serializable
enum class GifDisposalMethod {
    /** No disposal specified. */
    None,
    /** Do not dispose - leave graphic in place. */
    DoNotDispose,
    /** Restore to background colour. */
    RestoreToBackground,
    /** Restore to previous content. */
    RestoreToPrevious;

    companion object {
        fun fromCode(code: Int): GifDisposalMethod = when (code) {
            0 -> None; 1 -> DoNotDispose; 2 -> RestoreToBackground; 3 -> RestoreToPrevious
            else -> None
        }
    }
}

/**
 * Parsed Graphic Control Extension data (extension label `0xF9`, 4 data bytes).
 */
@Serializable
data class GifGraphicControl(
    val disposalMethod: GifDisposalMethod,
    val userInputFlag: Boolean,
    val transparencyFlag: Boolean,
    val delayTime: UShort,
    val transparentColorIndex: UByte,
)

/**
 * Parsed Application Extension data (extension label `0xFF`).
 */
@Serializable
data class GifApplicationExtension(
    /** 8-byte application identifier (e.g. "NETSCAPE"). */
    val applicationId: String,
    /** 3-byte authentication code (e.g. "2.0"). */
    val authCode: String,
    /** Application-specific sub-block data. */
    val data: Bytes,
)

// --- GIF file - complete on-disk representation ---

/**
 * Canonical representation of a GIF file as written to disk.
 *
 * ```
 * | Signature (6 B) | LSD (7 B) | [GCT] | Block* | Trailer (0x3B) |
 * ```
 */
@Serializable
data class GifRaw(
    /** GIF version (87a or 89a). */
    val version: GifVersion,
    /** Logical Screen Descriptor. */
    val screenDescriptor: GifLogicalScreenDescriptor,
    /** Global Color Table entries; empty when absent. */
    val globalColorTable: List<GifColor> = emptyList(),
    /** All blocks (images, extensions) in file order (excluding trailer). */
    val blocks: List<GifBlock> = emptyList(),
) : RawMediaStructure {

    // --- Binary serialization ---

    override fun toBytes(): Bytes {
        val sig = version.signature.encodeToByteArray()          // 6
        val lsd = screenDescriptor.toBytes()                      // 7
        val gctBytes = ByteArray(globalColorTable.size * 3)
        globalColorTable.forEachIndexed { i, c ->
            gctBytes[i * 3] = c.red.toByte()
            gctBytes[i * 3 + 1] = c.green.toByte()
            gctBytes[i * 3 + 2] = c.blue.toByte()
        }
        val blockParts = blocks.map { it.toBytes().data }
        val blockTotal = blockParts.sumOf { it.size }
        val totalSize = sig.size + lsd.size + gctBytes.size + blockTotal + 1 // +1 for trailer
        val out = ByteArray(totalSize)
        var pos = 0
        sig.copyInto(out, pos); pos += sig.size
        lsd.data.copyInto(out, pos); pos += lsd.size
        gctBytes.copyInto(out, pos); pos += gctBytes.size
        for (part in blockParts) { part.copyInto(out, pos); pos += part.size }
        out[pos] = TRAILER
        return out.asBytes()
    }

    companion object {
        /** GIF trailer byte. */
        const val TRAILER: Byte = 0x3B
    }
}

// --- Typed extension accessors ---

/** Canvas width from the Logical Screen Descriptor. */
val GifRaw.width: Pixels get() = Pixels(screenDescriptor.width.toInt())

/** Canvas height from the Logical Screen Descriptor. */
val GifRaw.height: Pixels get() = Pixels(screenDescriptor.height.toInt())

/** Number of image blocks (frames) in the file. */
val GifRaw.frameCount: Int get() = blocks.count { it.introducer.toInt() == 0x2C }

/** `true` for animated GIFs (more than one image block or NETSCAPE app extension). */
val GifRaw.isAnimated: Boolean
    get() = frameCount > 1 || applicationExtensions.any {
        it.applicationId == "NETSCAPE" && it.authCode == "2.0"
    }

/** Parse all Image Descriptor blocks in order. */
val GifRaw.imageDescriptors: List<GifImageDescriptor>
    get() = blocks
        .filter { it.introducer.toInt() == 0x2C && it.data.size >= 9 }
        .map { block ->
            val d = block.data.data
            fun readU16(off: Int): UShort =
                ((d[off + 1].toUInt() and 0xFFu) shl 8 or (d[off].toUInt() and 0xFFu)).toUShort()
            GifImageDescriptor(
                left = readU16(0), top = readU16(2),
                width = readU16(4), height = readU16(6),
                packed = d[8].toUByte(),
            )
        }

/** Parse all Graphic Control Extensions in order. */
val GifRaw.graphicControlExtensions: List<GifGraphicControl>
    get() = blocks
        .filter { it.introducer.toInt() == 0x21 && it.data.size >= 6 && it.data[0].toInt() and 0xFF == 0xF9 }
        .map { block ->
            val d = block.data.data
            val packed = d[2].toInt() and 0xFF
            val disposalCode = (packed shr 2) and 0x07
            val userInput = (packed and 0x02) != 0
            val transparency = (packed and 0x01) != 0
            val delay = ((d[4].toUInt() and 0xFFu) shl 8 or (d[3].toUInt() and 0xFFu)).toUShort()
            GifGraphicControl(
                disposalMethod = GifDisposalMethod.fromCode(disposalCode),
                userInputFlag = userInput,
                transparencyFlag = transparency,
                delayTime = delay,
                transparentColorIndex = d[5].toUByte(),
            )
        }

/** Parse all Application Extensions in order. */
val GifRaw.applicationExtensions: List<GifApplicationExtension>
    get() = blocks
        .filter { it.introducer.toInt() == 0x21 && it.data.size >= 14 && it.data[0].toInt() and 0xFF == 0xFF }
        .map { block ->
            val d = block.data.data
            val appId = d.decodeToString(2, 10)
            val authCode = d.decodeToString(10, 13)
            val remaining = if (d.size > 13) d.copyOfRange(13, d.size) else ByteArray(0)
            GifApplicationExtension(appId, authCode, Bytes(remaining))
        }
