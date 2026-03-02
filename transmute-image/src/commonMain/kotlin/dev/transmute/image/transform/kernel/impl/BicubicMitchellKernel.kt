package dev.transmute.image.transform.kernel.impl

import dev.transmute.image.transform.kernel.ResampleKernel

/**
 * Mitchell-Netravali bicubic filter (B = 1/3, C = 1/3).
 *
 * A good general-purpose cubic filter that balances sharpness, ringing,
 * and anisotropy. Recommended as the default for photographic content.
 *
 * Reference: Mitchell & Netravali, "Reconstruction Filters in Computer
 * Graphics", SIGGRAPH 1988.
 */
internal object BicubicMitchellKernel : ResampleKernel {
  override val support: Float = 2f

  // B = 1/3, C = 1/3 pre-computed coefficients:
  //   |x| < 1:  (12 - 9B - 6C)|x|^3  + (-18 + 12B + 6C)|x|^2              + (6 - 2B)
  //             = (7/6)|x|^3           + (-2)|x|^2                           + (16/9)  ... divided by 6
  // Using the standard Mitchell formula divided by 6:
  private const val P0 = 8f / 9f      // (6 - 2B) / 6
  private const val P2 = -2f          // (-18 + 12B + 6C) / 6
  private const val P3 = 7f / 6f      // (12 - 9B - 6C) / 6

  private const val Q0 = 8f / 3f      // (8B + 24C) / 6
  private const val Q1 = -4f          // (-12B - 48C) / 6
  private const val Q2 = 2f           // (6B + 30C) / 6
  private const val Q3 = -1f / 3f     // (-B - 6C) / 6

  override fun weight(x: Float): Float {
    val ax = if (x < 0f) -x else x
    return when {
      ax < 1f -> P0 + ax * ax * (P2 + ax * P3)
      ax < 2f -> Q0 + ax * (Q1 + ax * (Q2 + ax * Q3))
      else -> 0f
    }
  }
}
