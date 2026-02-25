@file:Suppress("unused")

package dev.transmute.model.view.image

import dev.transmute.model.core.BinarySerializable
import dev.transmute.model.structure.image.*
import dev.transmute.model.view.SeekableByteChannel

// ---------------------------------------------------------------------------
// Chunk index — maps each original chunk to its byte offset
// ---------------------------------------------------------------------------

/**
 * Records the byte position and size of a single chunk in a
 * serialized PNG file, so we can seek to it for surgical overwrites.
 */
internal class ChunkSlot(
    /** Chunk type tag (e.g. "IHDR"). */
    val type: String,
    /** Index into [Png.chunks]. */
    val chunkIndex: Int,
    /** Byte offset of the chunk's *length* field (start of the chunk). */
    val chunkOffset: Long,
    /** Byte offset of the chunk's *data* field. */
    val dataOffset: Long,
    /** Original data-field length in bytes. */
    val dataLength: Int,
    /** Total chunk size on disk (4 len + 4 type + data + 4 CRC). */
    val totalSize: Int,
)

/**
 * Build the [ChunkSlot] index from a [Png]'s chunk list.
 *
 * Offsets are computed from the known PNG layout:
 * `signature (8 B) | chunk₁ | chunk₂ | …`
 * where each chunk is `4 (length) + 4 (type) + data.size + 4 (CRC)`.
 */
internal fun buildChunkIndex(file: Png): List<ChunkSlot> {
    var offset = file.signature.size.toLong() // skip 8-byte signature
    return file.chunks.mapIndexed { i, chunk ->
        val dataLen = chunk.data.size
        val total = 4 + 4 + dataLen + 4
        val slot = ChunkSlot(
            type = chunk.type.value,
            chunkIndex = i,
            chunkOffset = offset,
            dataOffset = offset + 8, // past length + type
            dataLength = dataLen,
            totalSize = total,
        )
        offset += total
        slot
    }
}

// ---------------------------------------------------------------------------
// Dirty-entry: what to write for a changed chunk
// ---------------------------------------------------------------------------

/**
 * Describes a pending write for a single chunk slot.
 *
 * If [newData] has the same size as the original slot's data, the edit
 * can be applied in-place (seek + overwrite data + CRC). Otherwise the
 * file needs a tail-rewrite from this point onward.
 */
internal class ChunkPatch(
    val slot: ChunkSlot,
    val newData: ByteArray,
    val newCrc: UInt,
) {
    val sizeChanged: Boolean get() = newData.size != slot.dataLength
}

// ---------------------------------------------------------------------------
// StreamingPngView — surgical edits via SeekableByteChannel
// ---------------------------------------------------------------------------

/**
 * Streaming mutable view over a [Png] that performs surgical
 * writes to a [SeekableByteChannel].
 *
 * Unlike [MutablePngView] (which rebuilds the entire file in memory),
 * this view computes the minimal set of byte-range patches and writes
 * only the changed chunks.  For same-size edits (e.g. changing IHDR
 * width) this is a single 17-byte seek+write regardless of file size.
 *
 * Use the [editStreaming] extension on [Png]:
 *
 * ```kotlin
 * val channel = ByteArrayChannel(originalBytes)
 * pngFile.editStreaming(channel) {
 *     ihdr = ihdr.copy(width = 1920u, height = 1080u)
 * }
 * // channel now contains the patched file
 * ```
 */
class StreamingPngView internal constructor(
    source: Png,
    private val channel: SeekableByteChannel,
) : MutablePngView(source) {

    private val index: List<ChunkSlot> = buildChunkIndex(source)

    // ------------------------------------------------------------------
    // flush() — the core surgical-write engine
    // ------------------------------------------------------------------

    /**
     * Write all pending changes to the [channel].
     *
     * **Same-size edits** (e.g. modifying IHDR width): each changed
     * chunk is patched in-place with a single seek + write of
     * `data.size + 4` bytes (data + CRC).
     *
     * **Size-changing edits** (e.g. adding a text chunk, removing gAMA):
     * all chunks from the first size-change onward are rewritten
     * sequentially, then the channel is truncated to the new file size.
     *
     * **No edits**: nothing is written — zero I/O.
     */
    suspend fun flush() {
        // --- Collect patches for single-occurrence chunks ---
        val patches = mutableListOf<ChunkPatch>()
        val originalTypes = index.map { it.type }.toSet()

        for (slot in index) {
            if (slot.type in listChunkTypes || slot.type == "IEND") continue

            val current = currentSingleChunk(slot.type)
            val original = originalSingleChunk(slot.type)

            if (current != original && current is BinarySerializable) {
                val data = current.toBytes().data
                val typeBytes = slot.type.encodeToByteArray()
                patches += ChunkPatch(slot, data, crc32(typeBytes + data))
            }
            // If current == null and original != null → chunk removed → size change
            if (current == null && original != null) {
                // Sentinel patch with empty data to signal removal
                patches += ChunkPatch(slot, ByteArray(0), 0u)
            }
        }

        // --- Detect newly added single-occurrence chunks ---
        val newlyAdded = mutableListOf<Pair<String, BinarySerializable>>()
        for ((type, value) in singleChunkFields()) {
            if (value != null && type !in originalTypes) {
                newlyAdded += type to value
            }
        }

        val hasListChanges = listChunksDirty()
        val hasNewChunks = newlyAdded.isNotEmpty()

        // --- Fast path: no changes at all ---
        if (patches.isEmpty() && !hasListChanges && !hasNewChunks) return

        // --- Check if we can do purely in-place patches ---
        val allInPlace = !hasListChanges && !hasNewChunks &&
            patches.all { !it.sizeChanged } &&
            patches.none { it.newData.isEmpty() && it.slot.dataLength > 0 } // no removals

        if (allInPlace) {
            // Each patch is an independent seek + write (data + CRC only)
            for (patch in patches) {
                channel.position = patch.slot.dataOffset
                channel.write(patch.newData)
                channel.write(patch.newCrc.toBigEndianBytes())
            }
            return
        }

        // --- Slow path: rewrite from the first changed offset onward ---
        val iendSlot = index.first { it.type == "IEND" }

        val firstPatchOffset: Long = when {
            patches.isNotEmpty() -> patches.minOf { it.slot.chunkOffset }
            hasListChanges -> {
                val firstListSlot = index.firstOrNull { it.type in listChunkTypes }
                firstListSlot?.chunkOffset ?: iendSlot.chunkOffset
            }
            else -> iendSlot.chunkOffset // new chunks inserted before IEND
        }

        channel.position = firstPatchOffset

        // Walk source chunks in order; for each one, decide: write
        // original, write patched, skip (removed), or replace with
        // the current list.
        val patchByOffset = patches.associateBy { it.slot.chunkOffset }
        val listTypesEmitted = mutableSetOf<String>()

        for (slot in index) {
            if (slot.chunkOffset < firstPatchOffset) continue // before the rewrite point

            // --- List-type chunks: emit the entire current list on first occurrence ---
            if (slot.type in listChunkTypes) {
                if (slot.type !in listTypesEmitted) {
                    listTypesEmitted += slot.type
                    writeListChunks(slot.type)
                }
                // Skip remaining original slots of this type — already written.
                continue
            }

            // --- IEND: always last ---
            if (slot.type == "IEND") continue

            val patch = patchByOffset[slot.chunkOffset]
            if (patch != null) {
                if (patch.newData.isEmpty() && patch.slot.dataLength > 0) {
                    // Chunk removed — skip
                    continue
                }
                // Write patched chunk
                writeChunkRaw(slot.type, patch.newData, patch.newCrc)
            } else {
                // Write original chunk bytes unchanged
                writeOriginalChunk(slot)
            }
        }

        // --- Emit newly added single-occurrence chunks ---
        for ((type, value) in newlyAdded) {
            val data = value.toBytes().data
            val typeBytes = type.encodeToByteArray()
            writeChunkRaw(type, data, crc32(typeBytes + data))
        }

        // --- Emit list types that didn't exist in the original ---
        for (type in listChunkTypes) {
            if (type !in listTypesEmitted) {
                writeListChunks(type)
            }
        }

        // --- IEND ---
        val iendData = ByteArray(0)
        val iendCrc = crc32("IEND".encodeToByteArray())
        writeChunkRaw("IEND", iendData, iendCrc)

        // --- Truncate if the new file is shorter ---
        val newSize = channel.position
        if (newSize < channel.size) {
            channel.truncate(newSize)
        }
    }

    // ------------------------------------------------------------------
    // Low-level write helpers
    // ------------------------------------------------------------------

    private fun writeChunkRaw(type: String, data: ByteArray, crc: UInt) {
        channel.write(data.size.toUInt().toBigEndianBytes())
        channel.write(type.encodeToByteArray())
        channel.write(data)
        channel.write(crc.toBigEndianBytes())
    }

    private fun writeOriginalChunk(slot: ChunkSlot) {
        channel.write(source.chunks[slot.chunkIndex].toBytes().data)
    }

    private fun writeListChunks(type: String) {
        val items: List<BinarySerializable> = when (type) {
            "IDAT" -> idatChunks.map { buildChunkObj("IDAT", it.compressedData.data) }
            "tEXt" -> textChunks.map { buildChunkObj("tEXt", it.toBytes().data) }
            "zTXt" -> ztxtChunks.map { buildChunkObj("zTXt", it.toBytes().data) }
            "iTXt" -> itxtChunks.map { buildChunkObj("iTXt", it.toBytes().data) }
            "sPLT" -> spltChunks.map { buildChunkObj("sPLT", it.toBytes().data) }
            "fcTL" -> fctlChunks.map { buildChunkObj("fcTL", it.toBytes().data) }
            else -> emptyList()
        }
        for (item in items) {
            channel.write(item.toBytes().data)
        }
    }

    private fun buildChunkObj(type: String, data: ByteArray): PngChunk =
        buildChunk(type, data)

    // ------------------------------------------------------------------
    // build() is inherited from MutablePngView — reads this instance's
    // properties and reassembles the full Png. No override needed.
    // ------------------------------------------------------------------
}

// ---------------------------------------------------------------------------
// Big-endian helpers (internal, shared with MutablePngView)
// ---------------------------------------------------------------------------

internal fun UInt.toBigEndianBytes(): ByteArray = byteArrayOf(
    (this shr 24).toByte(),
    (this shr 16).toByte(),
    (this shr 8).toByte(),
    this.toByte(),
)

// ---------------------------------------------------------------------------
// editStreaming — the public DSL entry points
// ---------------------------------------------------------------------------

/**
 * Surgically patch a [SeekableByteChannel] that contains this PNG file.
 *
 * The [block] receives a [StreamingPngView] — the same `var` surface
 * as [MutablePngView].  When the block returns, only the changed
 * chunks are written to [channel]:
 *
 * - **Same-size edits** (e.g. change IHDR width): a single seek +
 *   17-byte overwrite, regardless of total file size.
 * - **Size-changing edits** (e.g. add a text chunk): a sequential
 *   rewrite from the first affected chunk to EOF, then truncate.
 *
 * ```kotlin
 * // Patch a file in-place
 * val channel = fileChannel("image.png")
 * pngFile.editStreaming(channel) {
 *     ihdr = ihdr.copy(width = 1920u)
 * }
 *
 * // Patch a byte buffer
 * val buf = ByteArrayChannel(pngBytes)
 * pngFile.editStreaming(buf) {
 *     time = PngTime(2026u, 2u, 24u, 12u, 0u, 0u)
 * }
 * val result = buf.toByteArray()
 * ```
 */
suspend fun Png.editStreaming(
    channel: SeekableByteChannel,
    block: StreamingPngView.() -> Unit,
) {
    StreamingPngView(this, channel).apply(block).flush()
}

/**
 * Convenience: surgically patch a copy of this file's bytes in memory.
 *
 * Returns the patched file as a new [ByteArray]. Useful for tests and
 * small files where you want the streaming write semantics without
 * managing a channel yourself.
 */
suspend fun Png.editStreaming(
    block: StreamingPngView.() -> Unit,
): ByteArray {
    val bytes = toBytes().data
    val channel = dev.transmute.model.view.ByteArrayChannel(bytes)
    editStreaming(channel, block)
    return channel.toByteArray()
}
