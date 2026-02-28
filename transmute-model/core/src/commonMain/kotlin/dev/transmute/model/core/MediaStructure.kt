package dev.transmute.model.core

import kotlinx.serialization.Serializable

/**
 * Developer-friendly, JSON-safe representation of a media file's structure.
 *
 * Unlike [RawMediaStructure], implementations of this interface must **not**
 * contain large opaque [Bytes] fields (e.g. IDAT compressed data, PCM samples,
 * JPEG entropy data). Instead, they use blob summaries (count + size) for such
 * content so the structure is always safe and cheap to serialize as JSON.
 *
 * Polymorphic serialization is handled by [MediaStructureSerializer] using the
 * [MediaStructureRegistry] dynamic dispatch table. The JSON wire format uses a
 * `type` + `value` envelope:
 * ```json
 * { "type": "transmute.png", "value": { "ihdr": { ... }, ... } }
 * ```
 *
 * Every concrete implementation must:
 * 1. Be annotated with `@Serializable`.
 * 2. Be registered via [MediaStructureRegistry.register] at startup.
 * 3. Never include a [Bytes] field with unbounded content.
 *
 * For in-place editing, each type provides a nested `Editor` class and an
 * `edit {}` extension function.
 */
@Serializable(with = MediaStructureSerializer::class)
interface MediaStructure
