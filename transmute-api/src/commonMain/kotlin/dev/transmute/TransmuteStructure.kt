@file:Suppress("unused")

package dev.transmute

import dev.transmute.audio.AudioFormat
import dev.transmute.image.ImageFormat
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.MediaFormat
import dev.transmute.model.core.UnknownFormat
import dev.transmute.model.core.asBytes
import dev.transmute.model.structure.BytesSink
import dev.transmute.model.structure.MediaStructure
import dev.transmute.model.structure.StructureReadException
import dev.transmute.model.structure.StructureReader
import dev.transmute.model.structure.StructureReaders
import dev.transmute.model.structure.StructureSink
import dev.transmute.structure.DefaultStructureReaders
import dev.transmute.video.VideoFormat

/**
 * API facade for reading raw file bytes into [MediaStructure] objects
 * and writing structures back to bytes or sinks.
 *
 * Access via [Transmute.structure]:
 * ```kotlin
 * val png: Png = Transmute.structure.read(pngBytes) as Png
 * val wav: Wav = Transmute.structure.read(wavBytes, AudioFormat.Wav)
 * val raw: Bytes = Transmute.structure.write(png)
 * ```
 */
class TransmuteStructure internal constructor(
    private val inspect: TransmuteInspect,
) {

    @Volatile
    private var defaultsInstalled = false

    // -- Read: format-explicit ------------------------------------------------

    /**
     * Parse [source] into a [MediaStructure] using the reader registered for [format].
     *
     * @throws StructureReadException if no reader is registered for [format]
     *   or if the bytes are malformed.
     */
    fun <S : MediaStructure> read(source: Bytes, format: MediaFormat<*, *>): S {
        installDefaultsIfEmpty()
        return StructureReaders.read(source, format)
    }

    /** Convenience overload accepting a [ByteArray]. */
    fun <S : MediaStructure> read(source: ByteArray, format: MediaFormat<*, *>): S =
        read(source.asBytes(), format)

    // -- Read: auto-detect ----------------------------------------------------

    /**
     * Detect the format of [source] (using the codec-level format detector)
     * and then parse it into the corresponding [MediaStructure].
     *
     * @throws StructureReadException if the format cannot be detected or
     *   no reader is available for the detected format.
     */
    fun read(source: Bytes): MediaStructure {
        installDefaultsIfEmpty()

        // Try format detection first for a precise reader lookup
        val format = inspect.detectFormat(source)
        if (format != UnknownFormat) {
            val reader = StructureReaders.readerFor<MediaStructure>(format)
            if (reader != null) return reader.read(source)
        }

        // Fall back to canRead()-based auto-detection
        return StructureReaders.readAuto(source)
    }

    /** Convenience overload accepting a [ByteArray]. */
    fun read(source: ByteArray): MediaStructure = read(source.asBytes())

    // -- Write ----------------------------------------------------------------

    /**
     * Serialize a [structure] to its canonical binary representation.
     *
     * Equivalent to calling `structure.toBytes()`, but provided here for
     * API symmetry with [read].
     */
    fun write(structure: MediaStructure): Bytes = structure.toBytes()

    /**
     * Serialize a [structure] and write it to [sink].
     */
    suspend fun writeTo(structure: MediaStructure, sink: StructureSink) {
        sink.write(structure)
        sink.flush()
    }

    // -- Registration ---------------------------------------------------------

    /**
     * Register a custom [reader] for the given [formats].
     *
     * Custom readers override built-in defaults for the same format.
     */
    fun register(reader: StructureReader<*>, vararg formats: MediaFormat<*, *>) {
        StructureReaders.register(reader, *formats)
    }

    // -- Install defaults lazily ----------------------------------------------

    private fun installDefaultsIfEmpty() {
        if (defaultsInstalled) return
        synchronized(this) {
            if (defaultsInstalled) return

            // Register built-in readers against their domain format objects
            StructureReaders.register(DefaultStructureReaders.wav, AudioFormat.Wav)
            StructureReaders.register(DefaultStructureReaders.mp3, AudioFormat.Mp3)
            StructureReaders.register(DefaultStructureReaders.flac, AudioFormat.Flac)
            StructureReaders.register(DefaultStructureReaders.png, ImageFormat.Png)
            StructureReaders.register(DefaultStructureReaders.jpeg, ImageFormat.Jpeg)
            StructureReaders.register(DefaultStructureReaders.bmp, ImageFormat.Bmp)

            defaultsInstalled = true
        }
    }
}
