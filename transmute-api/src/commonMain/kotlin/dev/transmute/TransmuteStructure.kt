@file:Suppress("unused")

package dev.transmute

import dev.transmute.audio.AudioFormat
import dev.transmute.image.ImageFormat
import dev.transmute.io.TChannel
import dev.transmute.io.TSink
import dev.transmute.io.TSource
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.MediaFormat
import dev.transmute.model.core.UnknownFormat
import dev.transmute.model.core.asBytes
import dev.transmute.model.structure.MediaStructure
import dev.transmute.model.structure.StructureReadException
import dev.transmute.model.structure.StructureReader
import dev.transmute.model.structure.StructureReaders
import dev.transmute.model.structure.StructureSink
import dev.transmute.structure.DefaultStructureReaders
import dev.transmute.video.VideoFormat

/**
 * API facade for reading raw file bytes into [MediaStructure] objects,
 * writing structures back to bytes or sinks, and performing in-place
 * transforms on files via channels.
 *
 * Access via [Transmute.structure]:
 * ```kotlin
 * // In-memory read
 * val png: Png = Transmute.structure.read(pngBytes, ImageFormat.Png)
 *
 * // From a TSource (suspending)
 * val src: TSource = fs.source(TPath.of("image.png"))
 * val png: Png = transmute.structure.read<Png>(src, ImageFormat.Png)
 *
 * // Lambda sugar — structure is the receiver
 * transmute.structure.read<Png>(src, ImageFormat.Png) { ihdr.width }
 *
 * // In-place transform via TChannel
 * val ch: TChannel = fs.channel(TPath.of("image.png"))
 * transmute.structure.transform<Png>(ch, ImageFormat.Png) {
 *     // 'this' is Png — return a modified copy
 * }
 * ```
 */
class TransmuteStructure internal constructor(
    private val inspect: TransmuteInspect,
) {

    @Volatile
    private var defaultsInstalled = false

    init {
        installDefaultsIfEmpty()
    }

    // ── Read: format-explicit (in-memory) ───────────────────────

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

    /**
     * Parse [source] and pass the result to [block], returning whatever [block] returns.
     *
     * ```kotlin
     * val width: Int = transmute.structure.read<Png>(pngBytes, ImageFormat.Png) {
     *     ihdr.width.toInt()
     * }
     * ```
     */
    inline fun <S : MediaStructure, R> read(
        source: Bytes,
        format: MediaFormat<*, *>,
        block: S.() -> R,
    ): R = read<S>(source, format).block()

    /** Lambda sugar overload accepting a [ByteArray]. */
    inline fun <S : MediaStructure, R> read(
        source: ByteArray,
        format: MediaFormat<*, *>,
        block: S.() -> R,
    ): R = read<S>(source.asBytes(), format).block()

    // ── Read: auto-detect (in-memory) ───────────────────────────

    /**
     * Detect the format of [source] and parse it into the corresponding [MediaStructure].
     *
     * @throws StructureReadException if the format cannot be detected or
     *   no reader is available for the detected format.
     */
    fun read(source: Bytes): MediaStructure {
        installDefaultsIfEmpty()

        val format = inspect.detectFormat(source)
        if (format != UnknownFormat) {
            val reader = StructureReaders.readerFor<MediaStructure>(format)
            if (reader != null) return reader.read(source)
        }

        return StructureReaders.readAuto(source)
    }

    /** Convenience overload accepting a [ByteArray]. */
    fun read(source: ByteArray): MediaStructure = read(source.asBytes())

    /**
     * Auto-detect format, parse, and pass the result to [block].
     */
    inline fun <R> read(source: Bytes, block: MediaStructure.() -> R): R =
        read(source).block()

    // ── Read: from TSource (suspending) ─────────────────────────

    /**
     * Read all bytes from [source] and parse into a [MediaStructure] of type [S].
     *
     * ```kotlin
     * val src: TSource = fs.source(path)
     * val png: Png = transmute.structure.read<Png>(src, ImageFormat.Png)
     * ```
     */
    suspend fun <S : MediaStructure> read(source: TSource, format: MediaFormat<*, *>): S {
        val bytes = source.readAll().asBytes()
        return read(bytes, format)
    }

    /**
     * Read from [source], parse, and pass the result to [block].
     *
     * ```kotlin
     * val width = transmute.structure.read<Png>(src, ImageFormat.Png) {
     *     ihdr.width.toInt()
     * }
     * ```
     */
    suspend inline fun <S : MediaStructure, R> read(
        source: TSource,
        format: MediaFormat<*, *>,
        block: S.() -> R,
    ): R = read<S>(source, format).block()

    /**
     * Read from [source] with auto-detected format.
     */
    suspend fun read(source: TSource): MediaStructure {
        val bytes = source.readAll().asBytes()
        return read(bytes)
    }

    /**
     * Read from [source] with auto-detected format, then apply [block].
     */
    suspend inline fun <R> read(source: TSource, block: MediaStructure.() -> R): R =
        read(source).block()

    // ── Write ───────────────────────────────────────────────────

    /**
     * Serialize a [structure] to its canonical binary representation.
     */
    fun write(structure: MediaStructure): Bytes = structure.toBytes()

    /**
     * Serialize a [structure] and write it to a [StructureSink].
     */
    suspend fun writeTo(structure: MediaStructure, sink: StructureSink) {
        sink.write(structure)
        sink.flush()
    }

    /**
     * Serialize a [structure] and write it to a [TSink].
     *
     * ```kotlin
     * val sink: TSink = fs.sink(TPath.of("output.png"))
     * transmute.structure.writeTo(pngStructure, sink)
     * ```
     */
    suspend fun writeTo(structure: MediaStructure, sink: TSink) {
        val bytes = structure.toBytes()
        sink.writeAll(bytes.data)
        sink.flush()
    }

    // ── Transform via TChannel ──────────────────────────────────

    /**
     * Read a structure from [channel], apply [transform], and write the
     * result back through the same channel.
     *
     * This is the primary API for in-place structure editing on files.
     * The [transform] lambda receives the parsed structure as its receiver
     * and must return the (possibly modified) structure.
     *
     * Use `.edit {}` inside the lambda to mutate via the format's mutable view:
     *
     * ```kotlin
     * val ch: TChannel = fs.channel(TPath.of("image.png"))
     * transmute.structure.transform<Png>(ch, ImageFormat.Png) {
     *     edit { ihdr = ihdr.copy(width = 100u) }
     * }
     * ```
     *
     * @param S the [MediaStructure] type
     * @param channel read-write channel to the file
     * @param format the media format to parse as
     * @param transform mutation block applied to the parsed structure
     * @return the transformed (immutable) structure
     */
    suspend fun <S : MediaStructure> transform(
        channel: TChannel,
        format: MediaFormat<*, *>,
        transform: S.() -> S,
    ): S {
        val bytes = channel.readAll().asBytes()
        val structure: S = read(bytes, format)
        val modified = structure.transform()
        val output = modified.toBytes()
        channel.writeAll(output.data)
        channel.flush()
        return modified
    }

    /**
     * Read a structure from [channel] with auto-detected format, apply
     * [transform], and write the result back.
     */
    suspend fun transform(
        channel: TChannel,
        transform: MediaStructure.() -> MediaStructure,
    ): MediaStructure {
        val bytes = channel.readAll().asBytes()
        val structure = read(bytes)
        val modified = structure.transform()
        val output = modified.toBytes()
        channel.writeAll(output.data)
        channel.flush()
        return modified
    }

    // ── Registration ────────────────────────────────────────────

    /**
     * Register a custom [reader] for the given [formats].
     *
     * Custom readers override built-in defaults for the same format.
     */
    fun register(reader: StructureReader<*>, vararg formats: MediaFormat<*, *>) {
        StructureReaders.register(reader, *formats)
    }

    // ── Install defaults lazily ─────────────────────────────────

    private fun installDefaultsIfEmpty() {
        if (defaultsInstalled) return
        synchronized(this) {
            if (defaultsInstalled) return

            // Image readers
            StructureReaders.register(DefaultStructureReaders.png, ImageFormat.Png)
            StructureReaders.register(DefaultStructureReaders.jpeg, ImageFormat.Jpeg)
            StructureReaders.register(DefaultStructureReaders.bmp, ImageFormat.Bmp)
            StructureReaders.register(DefaultStructureReaders.gif, ImageFormat.Gif)
            StructureReaders.register(DefaultStructureReaders.tiff, ImageFormat.Tiff)
            StructureReaders.register(DefaultStructureReaders.webp, ImageFormat.Webp)
            StructureReaders.register(DefaultStructureReaders.heif, ImageFormat.Heif, ImageFormat.Heic)
            StructureReaders.register(DefaultStructureReaders.avif, ImageFormat.Avif)

            // Audio readers
            StructureReaders.register(DefaultStructureReaders.wav, AudioFormat.Wav)
            StructureReaders.register(DefaultStructureReaders.mp3, AudioFormat.Mp3)
            StructureReaders.register(DefaultStructureReaders.flac, AudioFormat.Flac)
            StructureReaders.register(DefaultStructureReaders.aac, AudioFormat.Aac)
            StructureReaders.register(DefaultStructureReaders.m4a, AudioFormat.M4a)
            StructureReaders.register(DefaultStructureReaders.oggAudio, AudioFormat.Ogg)
            StructureReaders.register(DefaultStructureReaders.opus, AudioFormat.Opus)

            // Video readers
            StructureReaders.register(DefaultStructureReaders.mp4, VideoFormat.Mp4)
            StructureReaders.register(DefaultStructureReaders.mov, VideoFormat.Mov)
            StructureReaders.register(DefaultStructureReaders.webm, VideoFormat.Webm)
            StructureReaders.register(DefaultStructureReaders.mkv, VideoFormat.Mkv)
            StructureReaders.register(DefaultStructureReaders.avi, VideoFormat.Avi)

            defaultsInstalled = true
        }
    }
}
