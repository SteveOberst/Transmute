package dev.transmute.core.pipeline

import dev.transmute.core.ImageFormat
import dev.transmute.core.TransmuteContext
import dev.transmute.core.TransmuteLogger
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EncodeDecodePipelineTest {

  private data class NamedBytes(val name: String, val bytes: ByteArray)

  @Test
  fun `decode pipeline runs byte then ir handlers`() = runTest {
    val ctx = TransmuteContext(logger = TransmuteLogger.Noop)

    val pipeline: DecodePipeline<ByteArray, String> = PipelineBuilder.start<ByteArray>()
      .startWith { bytes, _ -> bytes + byteArrayOf('!'.code.toByte()) }
      .then { bytes, _ -> bytes.decodeToString() }
      .then { ir, _ -> ir + "-ir" }
      .build()

    val out = pipeline.run("hi".encodeToByteArray(), ctx)
    assertEquals("hi!-ir", out)
  }

  @Test
  fun `decode pipeline supports custom input types`() = runTest {
    val ctx = TransmuteContext(logger = TransmuteLogger.Noop)

    val pipeline: DecodePipeline<NamedBytes, String> =
      PipelineBuilder.start<NamedBytes>()
        .startWith { named, _ -> named.bytes }
        .then { bytes, _ -> "${bytes.decodeToString()}@ok" }
        .build()

    val out = pipeline.run(NamedBytes("x", "hi".encodeToByteArray()), ctx)
    assertEquals("hi@ok", out)
  }

  @Test
  fun `encode pipeline resolves output format and runs post-encode handlers`() = runTest {
    val ctx = TransmuteContext(logger = TransmuteLogger.Noop)

    val pipeline: EncodePipeline<String, ByteArray> =
      PipelineBuilder.start<String>()
        .startWith { ir, _ -> ir + "-pre" }
        .then { ir, _ -> ir.encodeToByteArray() }
        .then { bytes, _ -> bytes + byteArrayOf('!'.code.toByte()) }
        .build()

    val bytes = pipeline.run("hi", ctx)
    assertEquals("hi-pre!", bytes.decodeToString())
  }

  @Test
  fun `pipeline builder supports branching without shared mutation`() = runTest {
    val ctx = TransmuteContext(logger = TransmuteLogger.Noop)

    val base = PipelineBuilder.start<String>() startWith { s, _ -> s + "X" }

    val left = (base then { s, _ -> s + "L" }).build()
    val right = (base then { s, _ -> s + "R" }).build()

    assertEquals("hiXL", left.run("hi", ctx))
    assertEquals("hiXR", right.run("hi", ctx))
  }

  @Test
  fun `round trip decode transforms encode preserves format and order`() = runTest {
    val ctx = TransmuteContext(logger = TransmuteLogger.Noop)

    val decode: DecodePipeline<ByteArray, Decoded<ImageFormat, String>> =
      PipelineBuilder.start<ByteArray>()
        .startWith { bytes, _ -> bytes.decodeToString() }
        .then { ir, _ -> Decoded(ImageFormat.PNG, ir) }
        .build()

    val transforms = TransformPipeline<String>().apply {
      add(object : Transform<String> {
        override val id: TransformId = TransformId("t1")
        override suspend fun apply(ir: String, context: TransmuteContext): String = "($ir)"
      })
      add(object : Transform<String> {
        override val id: TransformId = TransformId("t2")
        override suspend fun apply(ir: String, context: TransmuteContext): String = "$ir!"
      })
    }.transforms

    val encode: EncodePipeline<Decoded<ImageFormat, String>, EncodedBytes<ImageFormat, dev.transmute.core.AnyFormatTag<ImageFormat>>> =
      PipelineBuilder.start<Decoded<ImageFormat, String>>()
        .startWith { decoded, _ ->
          EncodedBytes(dev.transmute.core.AnyFormatTag(decoded.format), decoded.ir.encodeToByteArray())
        }
        .build()

    val decoded = decode.run("hi".encodeToByteArray(), ctx)
    var ir = decoded.ir
    for (t in transforms) ir = t.apply(ir, ctx)
    val encoded = encode.run(Decoded(decoded.format, ir), ctx)

    assertEquals(ImageFormat.PNG, encoded.format)
    assertEquals("(hi)!".encodeToByteArray().decodeToString(), encoded.bytes.decodeToString())
  }
}
