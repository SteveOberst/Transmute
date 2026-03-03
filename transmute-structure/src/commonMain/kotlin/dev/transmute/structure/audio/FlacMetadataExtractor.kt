@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.metadata.vorbis.VorbisComment
import dev.transmute.model.metadata.vorbis.VorbisCommentMetadata
import dev.transmute.model.structure.audio.types.FlacRaw
import dev.transmute.model.structure.audio.types.vorbisCommentBlock

/**
 * Extract metadata from a parsed [FlacRaw].
 *
 * Supports:
 * - **Vorbis Comment** - from the `VorbisComment` metadata block
 */
fun FlacRaw.extractMetadata(): List<MediaMetadata> = buildList {
    extractVorbisComment()?.let(::add)
}

// -- Vorbis Comment extraction ------------------------------------------------

/**
 * FLAC Vorbis Comment block data layout (all little-endian):
 * ```
 * | vendor_length (4B) | vendor_string | comment_count (4B) |
 * | len_1 (4B) | "FIELD=value" | len_2 (4B) | "FIELD=value" | ... |
 * ```
 */
private fun FlacRaw.extractVorbisComment(): VorbisCommentMetadata? {
    val block = vorbisCommentBlock ?: return null
    return parseVorbisCommentBytes(block.data.data)
}

// -- Shared Vorbis Comment parser ---------------------------------------------

/**
 * Parse a raw Vorbis Comment byte sequence (used by both FLAC and Ogg).
 *
 * The format is always little-endian:
 * - vendorLength (u32LE) + vendor string (UTF-8)
 * - commentCount (u32LE)
 * - For each comment: length (u32LE) + "FIELD=value" (UTF-8)
 */
internal fun parseVorbisCommentBytes(d: ByteArray, offset: Int = 0): VorbisCommentMetadata? {
    if (d.size - offset < 8) return null
    var pos = offset

    fun u32le(): Int {
        if (pos + 4 > d.size) return 0
        val v = (d[pos].toInt() and 0xFF) or
                ((d[pos + 1].toInt() and 0xFF) shl 8) or
                ((d[pos + 2].toInt() and 0xFF) shl 16) or
                ((d[pos + 3].toInt() and 0xFF) shl 24)
        pos += 4
        return v
    }

    val vendorLen = u32le()
    if (pos + vendorLen > d.size) return null
    val vendor = d.decodeToString(pos, pos + vendorLen)
    pos += vendorLen

    val commentCount = u32le()
    val comments = mutableListOf<VorbisComment>()

    for (i in 0 until commentCount) {
        val len = u32le()
        if (pos + len > d.size) break
        val raw = d.decodeToString(pos, pos + len)
        pos += len
        val eq = raw.indexOf('=')
        if (eq > 0) {
            comments.add(VorbisComment(
                field = raw.substring(0, eq),
                value = raw.substring(eq + 1),
            ))
        } else {
            comments.add(VorbisComment(field = raw, value = ""))
        }
    }

    return VorbisCommentMetadata(vendor = vendor, comments = comments)
}
