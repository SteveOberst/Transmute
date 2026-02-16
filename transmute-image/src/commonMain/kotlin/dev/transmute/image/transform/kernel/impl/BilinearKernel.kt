package dev.transmute.image.transform.kernel.impl

import dev.transmute.image.transform.kernel.ResampleKernel

/**
 * Bilinear (triangle / tent) filter: `1 - |x|` for `|x| < 1`.
 *
 * Linear interpolation in each axis. Fast and produces smooth results
 * with minimal ringing, though softer than bicubic or Lanczos.
 */
internal object BilinearKernel : ResampleKernel {
  override val support: Float = 1f

  override fun weight(x: Float): Float {
    val ax = if (x < 0f) -x else x
    return if (ax < 1f) 1f - ax else 0f
  }
}
