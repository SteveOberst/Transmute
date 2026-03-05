package dev.transmute.common

import kotlin.jvm.JvmInline

/**
 * Bitmask that identifies which media domains are enabled.
 *
 * Combines [AUDIO], [VIDEO], and [IMAGE] flags using bitwise OR.
 * Use the predefined constants or combine them:
 *
 * ```kotlin
 * val domains = MediaDomain.AUDIO or MediaDomain.IMAGE
 * if (MediaDomain.VIDEO in domains) { /* video is enabled */ }
 * ```
 *
 * The special value [ALL] enables every domain; [NONE] disables all.
 */
@JvmInline
value class MediaDomain(val mask: Int) {

  /** Returns `true` if [other]'s flags are all set in this mask. */
  operator fun contains(other: MediaDomain): Boolean = mask and other.mask == other.mask

  /** Combine two domain masks. */
  infix fun or(other: MediaDomain): MediaDomain = MediaDomain(mask or other.mask)

  /** Intersect two domain masks. */
  infix fun and(other: MediaDomain): MediaDomain = MediaDomain(mask and other.mask)

  /** Remove [other]'s flags from this mask. */
  operator fun minus(other: MediaDomain): MediaDomain = MediaDomain(mask and other.mask.inv())

  /** Returns `true` if at least one domain flag is set. */
  fun isNotEmpty(): Boolean = mask != 0

  /** Returns `true` if no domain flags are set. */
  fun isEmpty(): Boolean = mask == 0

  override fun toString(): String = buildString {
    append("MediaDomain(")
    val parts = mutableListOf<String>()
    if (AUDIO in this@MediaDomain) parts += "AUDIO"
    if (VIDEO in this@MediaDomain) parts += "VIDEO"
    if (IMAGE in this@MediaDomain) parts += "IMAGE"
    append(parts.joinToString(" | ").ifEmpty { "NONE" })
    append(")")
  }

  companion object {
    /** No domains enabled. */
    val NONE = MediaDomain(0)

    /** Audio domain (AAC, M4A, Opus, FLAC, OGG, WAV, MP3). */
    val AUDIO = MediaDomain(1 shl 0)

    /** Video domain (MP4, MOV, WebM, AVI, MKV). */
    val VIDEO = MediaDomain(1 shl 1)

    /** Image domain (HEIF, AVIF, PNG, JPEG, WebP, GIF, BMP, TIFF). */
    val IMAGE = MediaDomain(1 shl 2)

    /** All domains enabled. */
    val ALL = MediaDomain(AUDIO.mask or VIDEO.mask or IMAGE.mask)

    /** Build a domain mask from individual boolean flags. */
    fun of(audio: Boolean = false, video: Boolean = false, image: Boolean = false): MediaDomain {
      var m = 0
      if (audio) m = m or AUDIO.mask
      if (video) m = m or VIDEO.mask
      if (image) m = m or IMAGE.mask
      return MediaDomain(m)
    }
  }
}
