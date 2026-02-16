package dev.transmute.image.transform.kernel

import dev.transmute.image.transform.kernel.impl.BilinearKernel
import dev.transmute.image.transform.kernel.impl.BicubicCatmullRomKernel
import dev.transmute.image.transform.kernel.impl.BicubicMitchellKernel
import dev.transmute.image.transform.kernel.impl.BoxKernel
import dev.transmute.image.transform.kernel.impl.Lanczos3Kernel
import dev.transmute.image.transform.kernel.impl.NearestKernel

/**
 * Maps [ResampleFilter] enum values to their concrete [ResampleKernel] implementations.
 */
object ResampleFactory {

  private val kernels: Map<ResampleFilter, ResampleKernel> = mapOf(
    ResampleFilter.NEAREST to NearestKernel,
    ResampleFilter.BOX to BoxKernel,
    ResampleFilter.BILINEAR to BilinearKernel,
    ResampleFilter.BICUBIC_MITCHELL to BicubicMitchellKernel,
    ResampleFilter.BICUBIC_CATMULL_ROM to BicubicCatmullRomKernel,
    ResampleFilter.LANCZOS3 to Lanczos3Kernel,
  )

  /** Returns the [ResampleKernel] for the given [filter]. */
  fun kernelFor(filter: ResampleFilter): ResampleKernel =
    kernels[filter] ?: error("No kernel registered for $filter")
}