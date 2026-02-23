package dev.transmute

import dev.transmute.core.OutputFormat
import dev.transmute.core.TransmuteContext
import dev.transmute.core.TransmuteLogger
import dev.transmute.core.asBytes
import dev.transmute.core.pipeline.Decoded
import dev.transmute.core.pipeline.EncodedBytes
import dev.transmute.core.pipeline.PipelineHandler
import dev.transmute.core.pipeline.Transform
import dev.transmute.core.pipeline.TransformId
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.CanonicalImageEncodeOptions
import dev.transmute.image.ColorInfo
import dev.transmute.image.ImageEncodeOptions
import dev.transmute.image.ImageFormat
import dev.transmute.image.ImageIR
import dev.transmute.image.PixelFormat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PipelinesIntegrationTest {

  @Test
  fun `image transmuter runs decode transforms encode in order`() = runTest {
    val t = Transmute.image {
      decode {
        pipeline(
          start = PipelineHandler { bytes, _ ->
            val width = bytes.data.firstOrNull()?.toInt() ?: 1
            val ir =
              ImageIR(
                buffer = ByteArrayPixelBuffer(ByteArray(4) { 0 }),
                width = width,
                height = 1,
                stride = 4,
                pixelFormat = PixelFormat.RGBA_8888,
                alphaSemantics = dev.transmute.image.AlphaSemantics.OPAQUE,
                colorInfo = ColorInfo(),
              )
            Decoded(ImageFormat.Jpeg, ir)
          },
        )
      }

      transform {
        add(object : Transform<ImageIR> {
          override val id: TransformId = TransformId("inc-width")
          override suspend fun apply(ir: ImageIR, context: TransmuteContext): ImageIR =
            ir.copy(width = ir.width + 1)
        })
      }

      encode {
        options(CanonicalImageEncodeOptions(outputFormat = OutputFormat.Exact(ImageFormat.Png)))

        pipeline(
          start = PipelineHandler { decoded, ctx ->
            val options = (ctx.encodeOptions as? ImageEncodeOptions) ?: CanonicalImageEncodeOptions()
            val outFormat = when (val declared = options.outputFormat) {
              OutputFormat.ORIGINAL -> decoded.format
              is OutputFormat.Exact -> declared.format
            }
            EncodedBytes(
              format = outFormat,
              bytes = "fmt=$outFormat width=${decoded.ir.width}".encodeToByteArray().asBytes(),
            )
          },
        )
      }

      logger(object : TransmuteLogger {
        override fun debug(message: String) {}
        override fun info(message: String) {}
        override fun warn(message: String) {}
        override fun error(message: String, throwable: Throwable?) {}
      })
    }

    val out = t.transmute(byteArrayOf(10).asBytes())
    assertEquals(ImageFormat.Png, out.format)
    assertEquals("fmt=Png width=11", out.bytes.data.decodeToString())
  }
}
