package dev.transmute.image.transform.kernel

/**
 * Supported resampling filters for [ImageResizeTransform][dev.transmute.image.transform.ImageResizeTransform].
 *
 * Each entry maps to a [ResampleKernel] via [ResampleFactory].
 */
enum class ResampleFilter {
  /** Nearest-neighbour: fastest, no interpolation. Crisp for pixel art; aliased for photos. */
  NEAREST,

  /** Box (averaging) filter: sums all source pixels that overlap the destination pixel. Good for exact integer down-scales. */
  BOX,

  /** Bilinear (triangle / tent): linear interpolation in both axes. Fast with acceptable quality. */
  BILINEAR,

  /** Mitchell-Netravali bicubic (B = 1/3, C = 1/3): good general-purpose sharpness without ringing. */
  BICUBIC_MITCHELL,

  /** Catmull-Rom bicubic (B = 0, C = 0.5): sharper than Mitchell, slight ringing. Good for upscaling. */
  BICUBIC_CATMULL_ROM,

  /** Lanczos windowed-sinc with a = 3: highest quality for photo down-scaling; slowest. */
  LANCZOS3,
}
