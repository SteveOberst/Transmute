package dev.transmute.video

import dev.transmute.core.MediaFormat

/**
 * Typed video container formats supported by Transmute.
 *
 * This replaces the old `enum class VideoFormat` with typed singleton objects.
 */
sealed interface VideoFormat : MediaFormat<VideoDecodeOptions, VideoEncodeOptions> {

  data object Mp4 : VideoFormat { override val mimeType: String = "video/mp4"; override val extension: String = "mp4" }
  data object Webm : VideoFormat { override val mimeType: String = "video/webm"; override val extension: String = "webm" }
  data object Mov : VideoFormat { override val mimeType: String = "video/quicktime"; override val extension: String = "mov" }
  data object Avi : VideoFormat { override val mimeType: String = "video/x-msvideo"; override val extension: String = "avi" }
  data object Mkv : VideoFormat { override val mimeType: String = "video/x-matroska"; override val extension: String = "mkv" }

  data object Unknown : VideoFormat { override val mimeType: String = "application/octet-stream"; override val extension: String = "bin" }

  companion object {
    val all: Set<VideoFormat> = setOf(Mp4, Webm, Mov, Avi, Mkv)
  }
}

