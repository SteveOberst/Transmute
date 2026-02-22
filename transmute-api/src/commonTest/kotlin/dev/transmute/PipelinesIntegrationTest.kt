package dev.transmute

import dev.transmute.core.AnyFormatTag
import dev.transmute.core.ImageFormat
import dev.transmute.core.OutputFormat
import dev.transmute.core.TransmuteContext
import dev.transmute.core.TransmuteLogger
import dev.transmute.core.pipeline.Decoded
import dev.transmute.core.pipeline.EncodedBytes
import dev.transmute.core.pipeline.Transform
import dev.transmute.core.pipeline.TransformId
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.CanonicalImageEncodeOptions
import dev.transmute.image.ColorInfo
import dev.transmute.image.ImageEncodeOptions
import dev.transmute.image.ImageIR
import dev.transmute.image.PixelFormat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PipelinesIntegrationTest {

  @Test
  fun `image transmuter runs decode transforms encode in order`() = runTest {
    val t = Transmute.image {
      encodeOptions(CanonicalImageEncodeOptions(outputFormat = OutputFormat.Exact(ImageFormat.PNG)))

      decode {
        startWith { bytes, _ ->
          val width = bytes.firstOrNull()?.toInt() ?: 1
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
          Decoded(ImageFormat.JPEG, ir)
        }
      }

      transform {
        add(object : Transform<ImageIR> {
          override val id: TransformId = TransformId("inc-width")
          override suspend fun apply(ir: ImageIR, context: TransmuteContext): ImageIR =
            ir.copy(width = ir.width + 1)
        })
      }

      encode {
        startWith { decoded, ctx ->
          val options = (ctx.encodeOptions as? ImageEncodeOptions) ?: CanonicalImageEncodeOptions()
          val outFormat = when (val declared = options.outputFormat) {
            OutputFormat.ORIGINAL -> decoded.format
            is OutputFormat.Exact -> declared.format
          }
          EncodedBytes(
            formatTag = AnyFormatTag(outFormat),
            bytes = "fmt=$outFormat width=${decoded.ir.width}".encodeToByteArray(),
          )
        }
      }

      logger(object : TransmuteLogger {
        override fun debug(message: String) {}
        override fun info(message: String) {}
        override fun warn(message: String) {}
        override fun error(message: String, throwable: Throwable?) {}
      })
    }

    val out = t.transmute(byteArrayOf(10))
    assertEquals(ImageFormat.PNG, out.format)
    assertEquals("fmt=PNG width=11", out.bytes.decodeToString())
  }
}
