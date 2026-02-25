@file:Suppress("unused")

package dev.transmute.structure

import dev.transmute.structure.audio.FlacStructureReader
import dev.transmute.structure.audio.Mp3StructureReader
import dev.transmute.structure.audio.WavStructureReader
import dev.transmute.structure.image.BmpStructureReader
import dev.transmute.structure.image.JpegStructureReader
import dev.transmute.structure.image.PngStructureReader

/**
 * Pre-built [StructureReader][dev.transmute.model.structure.StructureReader]
 * instances for all formats shipped in this module.
 *
 * These are **not** automatically registered — call [installDefaults] or
 * register them manually via [StructureReaders][dev.transmute.model.structure.StructureReaders].
 *
 * ```kotlin
 * // One-liner: register all built-in readers
 * DefaultStructureReaders.installDefaults()
 * ```
 */
object DefaultStructureReaders {

    // -- Audio --
    val wav  = WavStructureReader()
    val mp3  = Mp3StructureReader()
    val flac = FlacStructureReader()

    // -- Image --
    val png  = PngStructureReader()
    val jpeg = JpegStructureReader()
    val bmp  = BmpStructureReader()

    /**
     * The full list of built-in readers, in recommended sniff order.
     *
     * The order is: image readers first (fast magic-byte checks),
     * then audio readers (some require deeper inspection).
     */
    val all = listOf(png, jpeg, bmp, wav, mp3, flac)
}
