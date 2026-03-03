@file:Suppress("unused")

package dev.transmute.structure.common

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.RawMediaStructure
import dev.transmute.model.structure.StructureReader
import dev.transmute.model.structure.common.EbmlElement
import dev.transmute.model.structure.common.IsoBmffBox
import dev.transmute.model.structure.common.OggPage

/**
 * Generic [StructureReader] for ISO BMFF container formats
 * (MP4, MOV, HEIF, AVIF, M4A, etc.).
 *
 * All ISO BMFF formats share the same parsing step -- only the
 * resulting model type differs. Subclasses supply a [factory] that
 * wraps the parsed box list in the appropriate raw model.
 */
open class IsoBmffStructureReader<T : RawMediaStructure>(
    private val factory: (List<IsoBmffBox>) -> T,
) : StructureReader<T> {

    override fun read(source: Bytes): T {
        val boxes = source.data.parseIsoBmffBoxes()
        return factory(boxes)
    }
}

/**
 * Generic [StructureReader] for EBML container formats (MKV, WebM).
 *
 * All EBML formats share the same parsing step -- only the resulting
 * model type differs.
 */
open class EbmlStructureReader<T : RawMediaStructure>(
    private val factory: (List<EbmlElement>) -> T,
) : StructureReader<T> {

    override fun read(source: Bytes): T {
        val elements = source.data.parseEbmlElements()
        return factory(elements)
    }
}

/**
 * Generic [StructureReader] for Ogg container formats
 * (Ogg Vorbis, Opus).
 *
 * All Ogg formats share the same parsing step -- only the resulting
 * model type differs.
 */
open class OggStructureReader<T : RawMediaStructure>(
    private val factory: (List<OggPage>) -> T,
) : StructureReader<T> {

    override fun read(source: Bytes): T {
        val pages = source.data.parseOggPages()
        return factory(pages)
    }
}
