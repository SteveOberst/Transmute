package dev.transmute.model.core

/**
 * Canonical on-disk model for a media file.
 *
 * Every format-specific raw model (e.g. `PngRaw`, `Mp4Raw`) implements
 * this interface. The model mirrors the actual binary layout of the format -
 * like a C struct overlaid on memory-mapped file bytes.
 *
 * Because [RawMediaStructure] is [BinarySerializable], calling [toBytes]
 * on any raw model produces the exact bytes of a valid file that can be
 * written directly to disk.
 *
 * This is the renamed successor to the old `MediaStructure` interface.
 * The key difference from [MediaStructure] is that raw models may contain
 * large [Bytes] fields (e.g. IDAT compressed data, PCM audio samples)
 * and are therefore **not** safe to serialize as JSON.
 */
interface RawMediaStructure : BinarySerializable
