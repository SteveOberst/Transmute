package dev.transmute.image

/**
 * Platform-agnostic intermediate representation for a decoded image.
 *
 * Every image decoder produces an [ImageIR]; every encoder consumes one.
 * The pipeline transforms operate on this structure in-between.
 */
data class ImageIR(
  val buffer: PixelBuffer,
  val width: Int,
  val height: Int,
  val stride: Int,
  val pixelFormat: PixelFormat,
  val alphaSemantics: AlphaSemantics,
  val colorInfo: ColorInfo,
  val orientation: Orientation = Orientation.NORMAL,
  val metadata: ImageMetadata = ImageMetadata(),
)

// --- Pixel data ---

interface PixelBuffer {
  val sizeBytes: Long
}

data class ByteArrayPixelBuffer(val data: ByteArray) : PixelBuffer {
  override val sizeBytes: Long get() = data.size.toLong()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ByteArrayPixelBuffer) return false
    return data.contentEquals(other.data)
  }

  override fun hashCode(): Int = data.contentHashCode()
}

enum class PixelFormat(val bytesPerPixel: Int, val description: String) {
  RGBA_8888(4, "32-bit RGBA, 8 bits per channel"),
  RGB_888(3, "24-bit RGB, 8 bits per channel"),
  RGBA_F16(8, "64-bit RGBA, 16-bit float per channel"),
  RGBA_F32(16, "128-bit RGBA, 32-bit float per channel"),
}

enum class AlphaSemantics { STRAIGHT, PREMULTIPLIED, OPAQUE }

// --- Color ---

data class ColorInfo(
  val colorspace: Colorspace = Colorspace.SRGB,
  val transferFunction: TransferFunction = TransferFunction.SRGB,
  val iccProfileBytes: ByteArray? = null,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ColorInfo) return false
    if (colorspace != other.colorspace) return false
    if (transferFunction != other.transferFunction) return false
    if (iccProfileBytes != null) {
      if (other.iccProfileBytes == null) return false
      if (!iccProfileBytes.contentEquals(other.iccProfileBytes)) return false
    } else if (other.iccProfileBytes != null) return false
    return true
  }

  override fun hashCode(): Int {
    var result = colorspace.hashCode()
    result = 31 * result + transferFunction.hashCode()
    result = 31 * result + (iccProfileBytes?.contentHashCode() ?: 0)
    return result
  }
}

enum class Colorspace { SRGB, DISPLAY_P3, REC_2020, ADOBE_RGB }
enum class TransferFunction { SRGB, LINEAR, PQ, HLG }
enum class Orientation {
  /** No rotation needed — pixels are stored top-left first. */
  NORMAL,
  /** 90° clockwise (EXIF 6 — common for portrait photos on iOS). */
  ROTATE_90,
  /** 180° (EXIF 3 — upside-down). */
  ROTATE_180,
  /** 270° clockwise / 90° counter-clockwise (EXIF 8). */
  ROTATE_270,
}

// --- Metadata ---

data class ImageMetadata(
  val exifBlob: ByteArray? = null,
  val xmpBlob: ByteArray? = null,
  val appMetadata: Map<String, String> = emptyMap(),
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ImageMetadata) return false
    if (exifBlob != null) {
      if (other.exifBlob == null) return false
      if (!exifBlob.contentEquals(other.exifBlob)) return false
    } else if (other.exifBlob != null) return false
    if (xmpBlob != null) {
      if (other.xmpBlob == null) return false
      if (!xmpBlob.contentEquals(other.xmpBlob)) return false
    } else if (other.xmpBlob != null) return false
    return appMetadata == other.appMetadata
  }

  override fun hashCode(): Int {
    var result = exifBlob?.contentHashCode() ?: 0
    result = 31 * result + (xmpBlob?.contentHashCode() ?: 0)
    result = 31 * result + appMetadata.hashCode()
    return result
  }
}
