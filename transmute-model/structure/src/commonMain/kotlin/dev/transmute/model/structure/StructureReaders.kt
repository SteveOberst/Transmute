@file:Suppress("unused")

package dev.transmute.model.structure

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.MediaFormat

/**
 * Central registry for [StructureReader] instances.
 *
 * Readers are registered against one or more [MediaFormat] keys so
 * that callers can look up the right reader by format (e.g. after
 * running format detection).
 *
 * ```
 * // Registration (typically in transmute-api init or per-module installer)
 * StructureReaders.register(ImageFormat.Png, PngStructureReader())
 * StructureReaders.register(AudioFormat.Wav, WavStructureReader())
 *
 * // Lookup by format
 * val png: Png = StructureReaders.read(bytes, ImageFormat.Png)
 *
 * // Auto-detect via canRead()
 * val structure: MediaStructure = StructureReaders.readAuto(bytes)
 * ```
 */
object StructureReaders {

    private val readers = mutableListOf<StructureReader<*>>()
    private val byFormat = mutableMapOf<MediaFormat<*, *>, StructureReader<*>>()

    /**
     * Register a [reader] for one or more [formats].
     *
     * Later registrations for the same format silently override earlier ones.
     */
    fun register(reader: StructureReader<*>, vararg formats: MediaFormat<*, *>) {
        readers += reader
        for (format in formats) {
            byFormat[format] = reader
        }
    }

    /**
     * Return the registered reader for [format], or `null` if none is available.
     */
    @Suppress("UNCHECKED_CAST")
    fun <S : MediaStructure> readerFor(format: MediaFormat<*, *>): StructureReader<S>? =
        byFormat[format] as? StructureReader<S>

    /**
     * All formats for which a reader is registered.
     */
    val supportedFormats: Set<MediaFormat<*, *>>
        get() = byFormat.keys.toSet()

    /**
     * Read [source] using the reader registered for [format].
     *
     * @throws StructureReadException if no reader is registered for [format].
     */
    fun <S : MediaStructure> read(source: Bytes, format: MediaFormat<*, *>): S {
        val reader = readerFor<S>(format)
            ?: throw StructureReadException("No StructureReader registered for format: $format")
        return reader.read(source)
    }

    /**
     * Auto-detect the format by calling [StructureReader.canRead] on every
     * registered reader, then parse with the first match.
     *
     * Prefer [read] with an explicit format when the format is already known
     * (e.g. from `Transmute.inspect().detectFormat()`).
     *
     * @throws StructureReadException if no reader can parse [source].
     */
    fun readAuto(source: Bytes): MediaStructure {
        for (reader in readers) {
            if (!reader.canRead(source)) continue
            try {
                return reader.read(source)
            } catch (_: StructureReadException) {
                // canRead passed but parse failed — try next
            } catch (_: Exception) {
                // try next
            }
        }
        throw StructureReadException(
            "No registered StructureReader could parse the supplied bytes " +
                "(${source.size} bytes, tried ${readers.size} readers)"
        )
    }

    /**
     * Remove all registered readers. Primarily useful for tests.
     */
    fun clear() {
        readers.clear()
        byFormat.clear()
    }
}
