@file:Suppress("unused")

package dev.transmute.model.core

/**
 * Marks a type whose instances can be serialized to their canonical
 * binary representation — the exact bytes as they would appear on disk.
 *
 * For a [MediaStructure][dev.transmute.model.structure.MediaStructure] implementation
 * (e.g. `Png`), [toBytes] produces a valid file that can be written
 * directly to disk.  For sub-structures (e.g. a PNG chunk, an IHDR record)
 * it produces the corresponding fragment of the binary format.
 */
interface BinarySerializable {
    /** Encode this value to its canonical binary representation. */
    fun toBytes(): Bytes
}
