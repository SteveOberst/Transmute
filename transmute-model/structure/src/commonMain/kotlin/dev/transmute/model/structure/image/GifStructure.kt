@file:Suppress("unused")

package dev.transmute.model.structure.image

import dev.transmute.model.core.MediaStructure
import dev.transmute.model.core.ByteLength
import dev.transmute.model.core.Bytes
import dev.transmute.model.structure.image.types.GifApplicationExtension
import dev.transmute.model.structure.image.types.GifColor
import dev.transmute.model.structure.image.types.GifDisposalMethod
import dev.transmute.model.structure.image.types.GifGraphicControl
import dev.transmute.model.structure.image.types.GifImageDescriptor
import dev.transmute.model.structure.image.types.GifLogicalScreenDescriptor
import dev.transmute.model.structure.image.types.GifRaw
import dev.transmute.model.structure.image.types.GifVersion
import kotlinx.serialization.Serializable

/**
 * Structured representation of a GIF file, mirroring the on-disk layout.
 *
 * ```
 * "GIF87a" / "GIF89a"
 * Logical Screen Descriptor (7 B)
 * [Global Colour Table]
 * Blocks ...
 *   Image Descriptor + [Local Colour Table] + LZW data   <- pixel data excluded
 *   Graphic Control Extension
 *   Application Extension (NETSCAPE loop count, ...)
 *   Plain Text Extension
 *   Comment Extension
 * Trailer (0x3B)
 * ```
 *
 * LZW-compressed pixel data inside image blocks is excluded; only the
 * [dev.transmute.model.structure.image.types.GifImageDescriptor] geometry and optional local colour-table flag are kept.
 */
@Serializable
data class GifStructure(
    /** GIF specification version (87a or 89a). */
    val version: GifVersion,
    /** Logical Screen Descriptor - canvas dimensions and global colour-table info. */
    val screenDescriptor: GifLogicalScreenDescriptor,
    /** Global Colour Table entries; empty when the global colour table is absent. */
    val globalColorTable: List<GifColor>,
    /** All blocks (image blocks + extension blocks) in file order. */
    val blocks: List<GifBlockSummary>,
) : MediaStructure

/** Ordered, JSON-safe summary of a single GIF block (payload bytes excluded). */
@Serializable
data class GifBlockSummary(
    /** Zero-based index in [dev.transmute.model.structure.image.types.GifRaw.blocks]. */
    val index: Int,
    /** Block introducer byte (`0x2C` image, `0x21` extension). */
    val introducer: Int,
    /** Human-readable block kind. */
    val kind: GifBlockKind,
    /** Total raw block payload size in bytes (excludes the introducer byte). */
    val dataBytes: ByteLength,
    /** Extension label byte for extension blocks (e.g. `0xF9` GCE, `0xFF` application). */
    val extensionLabel: Int? = null,
    /** Decoded extension kind derived from [extensionLabel]. */
    val extensionKind: GifExtensionKind? = null,
    /** Image descriptor for image blocks, if parsable. */
    val imageDescriptor: GifImageDescriptor? = null,
    /** Graphic Control Extension parsed from extension blocks, if present. */
    val graphicControl: GifGraphicControl? = null,
    /** Application Extension parsed from extension blocks, if present. */
    val applicationExtension: GifApplicationExtension? = null,
)

@Serializable
enum class GifBlockKind {
    Image,
    Extension,
    Unknown,
}

@Serializable
enum class GifExtensionKind {
    GraphicControl,
    Application,
    Comment,
    PlainText,
    Unknown,
}

/**
 * Parse this [dev.transmute.model.structure.image.types.GifRaw] into a [GifStructure].
 */
fun GifRaw.toStructure(): GifStructure =
    GifStructure(
        version = version,
        screenDescriptor = screenDescriptor,
        globalColorTable = globalColorTable,
        blocks = blocks.mapIndexed { idx, block ->
            val introducer = block.introducer.toInt() and 0xFF
            val kind = when (introducer) {
                0x2C -> GifBlockKind.Image
                0x21 -> GifBlockKind.Extension
                else -> GifBlockKind.Unknown
            }

            val extLabel: Int? =
                if (introducer == 0x21 && block.data.size >= 1) (block.data[0].toInt() and 0xFF) else null
            val extKind: GifExtensionKind? =
                if (extLabel == null) null
                else when (extLabel) {
                    0xF9 -> GifExtensionKind.GraphicControl
                    0xFF -> GifExtensionKind.Application
                    0xFE -> GifExtensionKind.Comment
                    0x01 -> GifExtensionKind.PlainText
                    else -> GifExtensionKind.Unknown
                }

            val img = if (introducer == 0x2C && block.data.size >= 9) {
                val d = block.data.data
                fun readU16(off: Int): UShort =
                    ((d[off + 1].toUInt() and 0xFFu) shl 8 or (d[off].toUInt() and 0xFFu)).toUShort()
                GifImageDescriptor(
                    left = readU16(0),
                    top = readU16(2),
                    width = readU16(4),
                    height = readU16(6),
                    packed = d[8].toUByte(),
                )
            } else {
                null
            }

            val gce = if (introducer == 0x21 && block.data.size >= 6 && (block.data[0].toInt() and 0xFF) == 0xF9) {
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
            } else {
                null
            }

            val app = if (introducer == 0x21 && block.data.size >= 14 && (block.data[0].toInt() and 0xFF) == 0xFF) {
                val d = block.data.data
                val appId = d.decodeToString(2, 10)
                val authCode = d.decodeToString(10, 13)
                val remaining = if (d.size > 13) d.copyOfRange(13, d.size) else ByteArray(0)
                GifApplicationExtension(appId, authCode, Bytes(remaining))
            } else {
                null
            }

            GifBlockSummary(
                index = idx,
                introducer = introducer,
                kind = kind,
                dataBytes = ByteLength(block.data.size.toLong()),
                extensionLabel = extLabel,
                extensionKind = extKind,
                imageDescriptor = img,
                graphicControl = gce,
                applicationExtension = app,
            )
        },
    )
