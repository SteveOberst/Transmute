package dev.transmute.model.structure

import dev.transmute.model.core.BinarySerializable

/**
 * Marker interface for canonical representations of media files.
 *
 * Each format-specific file model (e.g. `Png`, `Mp3`)
 * implements this interface and defines its own structure that
 * mirrors the actual on-disk layout of that format — like a
 * C struct you could overlay onto memory-mapped file bytes.
 *
 * Because every [MediaStructure] is [BinarySerializable], calling
 * [toBytes] on any file model produces the exact bytes of a
 * valid file that can be written directly to disk.
 */
interface MediaStructure : BinarySerializable

