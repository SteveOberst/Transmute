package dev.transmute.core

/** Type-level tags for concrete [VideoFormat] values. */
sealed interface VideoFormatTag : FormatTag<VideoFormat> {
  data object Mp4 : VideoFormatTag { override val format: VideoFormat = VideoFormat.MP4 }
  data object Webm : VideoFormatTag { override val format: VideoFormat = VideoFormat.WEBM }
  data object Mov : VideoFormatTag { override val format: VideoFormat = VideoFormat.MOV }
  data object Avi : VideoFormatTag { override val format: VideoFormat = VideoFormat.AVI }
  data object Mkv : VideoFormatTag { override val format: VideoFormat = VideoFormat.MKV }
}
