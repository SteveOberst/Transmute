package dev.transmute.image.transform.kernel

/**
 * A 1-D resampling kernel used for separable image resize.
 *
 * Implementations define a symmetric weight function `weight(x)` and the
 * [support] radius (the maximum `|x|` for which the weight is non-zero).
 *
 * The resize algorithm evaluates the kernel independently in the horizontal
 * and vertical directions (separable convolution), so only a 1-D function
 * is needed.
 */
interface ResampleKernel {

  /** Half-width of the kernel window. Samples outside `[-support, support]` are zero. */
  val support: Float

  /**
   * Returns the filter weight at distance [x] from the centre.
   *
   * Callers guarantee `|x| <= support`; implementations may still return 0
   * for out-of-range values for safety.
   */
  fun weight(x: Float): Float
}
