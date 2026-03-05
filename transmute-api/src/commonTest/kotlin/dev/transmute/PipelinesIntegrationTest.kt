package dev.transmute

import dev.transmute.codec.OutputFormat
import dev.transmute.codec.pipeline.Decoded
import dev.transmute.codec.pipeline.EncodedBytes
import dev.transmute.codec.pipeline.Transform
import dev.transmute.codec.pipeline.TransformId
import dev.transmute.common.PipelineContext
import dev.transmute.common.TransmuteLogger
import dev.transmute.image.AlphaSemantics
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.CanonicalImageEncodeOptions
import dev.transmute.image.ColorInfo
import dev.transmute.image.ImageEncodeOptions
import dev.transmute.image.ImageFormat
import dev.transmute.image.ImageIR
import dev.transmute.image.PixelFormat
import dev.transmute.model.core.asBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class PipelinesIntegrationTest {

  @Test
  fun `image transmuter runs decode transforms encode in order`() = runTest {
    val t = Transmute.image {
      decode {
        pipeline(
          initial = { bytes, _ ->
            val sourceData = bytes.readAll()
            val width = sourceData.firstOrNull()?.toInt()
              ?: 1
            val ir =
              ImageIR(
                buffer = ByteArrayPixelBuffer(ByteArray(4)),
                width = width,
                height = 1,
                stride = 4,
                pixelFormat = PixelFormat.RGBA_8888,
                alphaSemantics = AlphaSemantics.OPAQUE,
                colorInfo = ColorInfo(),
              )
            Decoded(ImageFormat.Jpeg, ir)
          },
        )
      }

      transform {
        add(object : Transform<ImageIR> {
          override val id: TransformId = TransformId("inc-width")
          override suspend fun apply(ir: ImageIR, context: PipelineContext): ImageIR = ir.copy(width = ir.width + 1)
        })
      }

      encode {
        options(CanonicalImageEncodeOptions(outputFormat = OutputFormat.Exact(ImageFormat.Png)))

        pipeline(
          initial = { decoded, ctx ->
            val options = (ctx.encodeOptions as? ImageEncodeOptions)
              ?: CanonicalImageEncodeOptions()
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
