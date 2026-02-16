package dev.transmute.image.transform.kernel.impl

import dev.transmute.image.transform.kernel.ResampleKernel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Lanczos windowed-sinc filter with a = 3 (3-lobe).
 *
 * The theoretically best reconstruction filter for band-limited signals.
 * Produces the sharpest results of any filter here, with minimal aliasing,
 * at the cost of slight ringing around high-contrast edges.
 *
 * Recommended for high-quality photo down-scaling.
 */
internal object Lanczos3Kernel : ResampleKernel {
  override val support: Float = 3f

  private const val A = 3.0

  override fun weight(x: Float): Float {
    val ax = abs(x.toDouble())
    if (ax >= A) return 0f
    if (ax < 1e-7) return 1f

    val pix = PI * ax
    val pixOverA = pix / A
    return ((sin(pix) / pix) * (sin(pixOverA) / pixOverA)).toFloat()
  }
}
