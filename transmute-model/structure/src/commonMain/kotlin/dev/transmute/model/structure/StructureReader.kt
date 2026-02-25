@file:Suppress("unused")

package dev.transmute.model.structure

import dev.transmute.model.core.Bytes

/**
 * Reads raw file bytes into a [MediaStructure] of type [S].
 *
 * Each format has its own reader implementation that knows how to
 * parse the binary layout of that format into a typed structure.
 *
 * Readers are pure functions over bytes — they do **not** decode
 * pixel/sample data. They only parse the structural envelope
 * (headers, chunks, atoms, etc.) so the result mirrors the on-disk
 * layout as a Kotlin data class.
 *
 * ```
 *  val reader: StructureReader<Png> = PngStructureReader()
 *  val png: Png = reader.read(pngBytes)
 *  png.ihdr.width  // parsed IHDR width
 * ```
 */
interface StructureReader<out S : MediaStructure> {

    /**
     * Quick check — can this reader likely parse [source]?
     *
     * Implementations should check magic bytes / signatures without
     * fully parsing the file. Returns `false` when the bytes clearly
     * do **not** belong to this format.
     */
    fun canRead(source: Bytes): Boolean

    /**
     * Parse [source] into a typed structure.
     *
     * @throws StructureReadException if the bytes are malformed or
     *   do not match the expected format.
     */
    fun read(source: Bytes): S
}

/**
 * Thrown when a [StructureReader] cannot parse the supplied bytes.
 */
class StructureReadException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
