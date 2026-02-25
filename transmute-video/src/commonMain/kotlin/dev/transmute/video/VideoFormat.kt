package dev.transmute.video

import dev.transmute.model.core.ContainerFamily
import dev.transmute.model.core.MediaFormat

/**
 * Typed video container formats supported by Transmute.
 *
 * This replaces the old `enum class VideoFormat` with typed singleton objects.
 */
sealed interface VideoFormat : MediaFormat<VideoDecodeOptions, VideoEncodeOptions> {

  data object Mp4 : VideoFormat {
    override val label: String = "MP4"
    override val mimeType: String = "video/mp4"
    override val extension: String = "mp4"
    override val containerFamily: ContainerFamily = ContainerFamily.IsoBmff
  }
  data object Webm : VideoFormat {
    override val label: String = "WebM"
    override val mimeType: String = "video/webm"
    override val extension: String = "webm"
    override val containerFamily: ContainerFamily = ContainerFamily.Ebml
  }
  data object Mov : VideoFormat {
    override val label: String = "MOV"
    override val mimeType: String = "video/quicktime"
    override val extension: String = "mov"
    override val containerFamily: ContainerFamily = ContainerFamily.IsoBmff
  }
  data object Avi : VideoFormat {
    override val label: String = "AVI"
    override val mimeType: String = "video/x-msvideo"
    override val extension: String = "avi"
    override val containerFamily: ContainerFamily = ContainerFamily.Riff
  }
  data object Mkv : VideoFormat {
    override val label: String = "MKV"
    override val mimeType: String = "video/x-matroska"
    override val extension: String = "mkv"
    override val containerFamily: ContainerFamily = ContainerFamily.Ebml
  }

  data object Unknown : VideoFormat {
    override val label: String = "Unknown"
    override val mimeType: String = "application/octet-stream"
    override val extension: String = "bin"
  }

  companion object {
    val all: Set<VideoFormat> = setOf(Mp4, Webm, Mov, Avi, Mkv)
  }
}

