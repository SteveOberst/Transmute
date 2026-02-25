@file:Suppress("unused")

package dev.transmute.model.view

import dev.transmute.model.structure.MediaStructure

// ---------------------------------------------------------------------------
// StructureView — read-only view over a MediaStructure
// ---------------------------------------------------------------------------

/**
 * Read-only view over a [MediaStructure].
 *
 * Format-specific interfaces (e.g. `PngView`) extend this marker
 * with `val` properties for every well-known field.  Code that
 * only needs to inspect — never mutate — a file can accept
 * a `StructureView<F>` and work with any view tier (immutable, mutable,
 * or streaming).
 *
 * @param F The concrete [MediaStructure] subtype this view exposes.
 */
interface StructureView<F : MediaStructure>

// ---------------------------------------------------------------------------
// MutableStructureView — read/write view that can rebuild the file
// ---------------------------------------------------------------------------

/**
 * Mutable view for editing a [MediaStructure] via a DSL block.
 *
 * This follows the same "mutator" pattern used by
 * [EncodeOptionsMutator][dev.transmute.EncodeOptionsMutator] in
 * transmute-api: create a mutable snapshot, apply changes inside
 * a lambda, then [build] an immutable result.
 *
 * Format-specific implementations (e.g. `MutablePngView`) expose
 * `var` properties for every well-known chunk / field that can be
 * modified.  Calling [build] reassembles the file's binary
 * structure with correct checksums, lengths, etc.
 *
 * Usage:
 * ```kotlin
 * val edited = pngFile.edit {
 *     ihdr = ihdr.copy(width = 1920u, height = 1080u)
 * }
 * ```
 *
 * @param F The concrete [MediaStructure] subtype this view wraps.
 */
interface MutableStructureView<F : MediaStructure> : StructureView<F> {
    /**
     * Materialise all pending changes into a new, immutable [F].
     *
     * Called automatically by the `edit` extension; callers do not
     * normally invoke this directly.
     */
    fun build(): F
}
