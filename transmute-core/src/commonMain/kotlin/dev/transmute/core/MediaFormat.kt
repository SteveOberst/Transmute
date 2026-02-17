package dev.transmute.core

/**
 * Type-safe representation of a media format across image, audio, and video.
 *
 * Every format carries its standard MIME type and file extension.
 * Use the concrete enum subtypes ([ImageFormat], [AudioFormat], [VideoFormat])
 * throughout the pipeline instead of raw `String` MIME types.
 */
sealed interface MediaFormat {
  val mimeType: String
  val extension: String
}

// --- Image ---

/**
 * All image formats the pipeline can encounter on iOS and Android.
 *
 * Covers:
 * - **JPEG** - universal camera format
 * - **PNG** - screenshots, stickers, transparency
 * - **WebP** - Android default since 4.0, WhatsApp/Telegram
 * - **HEIF / HEIC** - iOS default since iOS 11
 * - **AVIF** - Android 12+ next-gen
 * - **GIF** - animated images
 * - **BMP** - legacy
 * - **TIFF** - ProRAW on iOS
 */
enum class ImageFormat(override val mimeType: String, override val extension: String) : MediaFormat {
  JPEG("image/jpeg", "jpg"),
  PNG("image/png", "png"),
  WEBP("image/webp", "webp"),
  HEIF("image/heif", "heif"),
  HEIC("image/heic", "heic"),
  AVIF("image/avif", "avif"),
  GIF("image/gif", "gif"),
  BMP("image/bmp", "bmp"),
  TIFF("image/tiff", "tiff"),
  UNKNOWN("application/octet-stream", "bin"),
}

// --- Audio ---

/**
 * Common audio formats for mobile media.
 */
enum class AudioFormat(override val mimeType: String, override val extension: String) : MediaFormat {
  MP3("audio/mpeg", "mp3"),
  AAC("audio/aac", "aac"),
  WAV("audio/wav", "wav"),
  OGG("audio/ogg", "ogg"),
  FLAC("audio/flac", "flac"),
  M4A("audio/mp4", "m4a"),
  OPUS("audio/opus", "opus"),
  UNKNOWN("application/octet-stream", "bin"),
}

// --- Video ---

/**
 * Common video container formats for mobile media.
 */
enum class VideoFormat(override val mimeType: String, override val extension: String) : MediaFormat {
  MP4("video/mp4", "mp4"),
  WEBM("video/webm", "webm"),
  MOV("video/quicktime", "mov"),
  AVI("video/x-msvideo", "avi"),
  MKV("video/x-matroska", "mkv"),
  UNKNOWN("application/octet-stream", "bin"),
}
