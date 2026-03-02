package dev.transmute.image.transform.kernel.impl

import dev.transmute.image.transform.kernel.ResampleKernel

/**
 * Catmull-Rom bicubic filter (B = 0, C = 0.5).
 *
 * Sharper than Mitchell-Netravali with slightly more ringing.
 * Particularly good for upscaling where edge preservation matters.
 *
 * This is the same cubic used by CSS `image-rendering: smooth` and
 * many GPU texture samplers.
 */
internal object BicubicCatmullRomKernel : ResampleKernel {
  override val support: Float = 2f

  override fun weight(x: Float): Float {
    val ax = if (x < 0f) -x else x
    val ax2 = ax * ax
    val ax3 = ax2 * ax
    return when {
      ax < 1f -> 1.5f * ax3 - 2.5f * ax2 + 1f
      ax < 2f -> -0.5f * ax3 + 2.5f * ax2 - 4f * ax + 2f
      else -> 0f
    }
  }
}
