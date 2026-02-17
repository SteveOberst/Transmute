package dev.transmute.image.transform.kernel.impl

import dev.transmute.image.transform.kernel.ResampleKernel

/**
 * Nearest-neighbour: returns 1 inside `[-0.5, 0.5)`, 0 elsewhere.
 *
 * Zero interpolation - simply picks the closest source pixel.
 * Best for pixel art or when speed matters more than quality.
 */
internal object NearestKernel : ResampleKernel {
  override val support: Float = 0.5f

  override fun weight(x: Float): Float {
    val ax = if (x < 0f) -x else x
    return if (ax < 0.5f) 1f else 0f
  }
}
