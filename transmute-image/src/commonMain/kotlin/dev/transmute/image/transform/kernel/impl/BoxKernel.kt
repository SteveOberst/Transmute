package dev.transmute.image.transform.kernel.impl

import dev.transmute.image.transform.kernel.ResampleKernel

/**
 * Box (averaging) filter: constant weight inside `[-0.5, 0.5]`.
 *
 * Equivalent to area averaging when down-scaling by an integer factor.
 * Produces softer results than nearest-neighbour with minimal cost.
 */
internal object BoxKernel : ResampleKernel {
  override val support: Float = 0.5f

  override fun weight(x: Float): Float {
    val ax = if (x < 0f) -x else x
    return if (ax <= 0.5f) 1f else 0f
  }
}
