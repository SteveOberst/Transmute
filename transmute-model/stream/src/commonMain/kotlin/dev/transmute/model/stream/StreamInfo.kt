@file:Suppress("unused")

package dev.transmute.model.stream

import dev.transmute.model.core.*

/**
 * Information about a video stream.
 */
data class VideoStreamInfo(
    val width: Pixels,
    val height: Pixels,
    val frameRate: Rational? = null,
    val bitrate: Bitrate? = null,
    val codec: CodecDescriptor? = null,
    val colorPrimaries: ColorPrimaries? = null,
    val transferCharacteristics: TransferCharacteristics? = null,
    val matrixCoefficients: MatrixCoefficients? = null,
)

/**
 * Information about an audio stream.
 */
data class AudioStreamInfo(
    val sampleRate: Hertz,
    val channels: Channels,
    val bitsPerSample: BitsPerSample? = null,
    val bitrate: Bitrate? = null,
    val codec: CodecDescriptor? = null,
    val durationMicros: DurationMicros? = null,
)

/**
 * Information about an image stream (for container formats
 * that carry images as streams, e.g. HEIF).
 */
data class ImageStreamInfo(
    val width: Pixels,
    val height: Pixels,
    val bitsPerPixel: Int? = null,
    val codec: CodecDescriptor? = null,
)

/**
 * Unified stream descriptor combining type, identity, and
 * type-specific information.
 */
data class StreamInfo(
    val id: StreamId,
    val type: StreamType,
    val codec: CodecDescriptor? = null,
    val durationMicros: DurationMicros? = null,
    val bitrate: Bitrate? = null,
    val language: LanguageTag? = null,
    val video: VideoStreamInfo? = null,
    val audio: AudioStreamInfo? = null,
    val image: ImageStreamInfo? = null,
    val extensions: List<ModelExtension> = emptyList(),
)
