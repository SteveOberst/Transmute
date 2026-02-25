package dev.transmute.codec.pipeline

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.MediaFormat
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.core.NoEncodeOptions
import dev.transmute.model.core.asBytes
import dev.transmute.common.PipelineContext
import dev.transmute.common.TransmuteLogger
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EncodeDecodePipelineTest {

  private data class NamedBytes(val name: String, val bytes: Bytes)

  private object TestFormat : MediaFormat<NoDecodeOptions, NoEncodeOptions> {
    override val label: String = "Test"
    override val mimeType: String = "application/test"
    override val extension: String = "test"
  }

  @Test
  fun `decode pipeline runs byte then ir handlers`() = runTest {
    val ctx = PipelineContext(logger = TransmuteLogger.Noop)

    val pipeline: DecodePipeline<Bytes, String> = PipelineBuilder.start<Bytes>()
      .startWith { bytes, _ -> (bytes.data + byteArrayOf('!'.code.toByte())).asBytes() }
      .then { bytes, _ -> bytes.data.decodeToString() }
      .then { ir, _ -> ir + "-ir" }
      .build()

    val out = pipeline.run("hi".encodeToByteArray().asBytes(), ctx)
    assertEquals("hi!-ir", out)
  }

  @Test
  fun `decode pipeline supports custom input types`() = runTest {
    val ctx = PipelineContext(logger = TransmuteLogger.Noop)

    val pipeline: DecodePipeline<NamedBytes, String> =
      PipelineBuilder.start<NamedBytes>()
        .startWith { named, _ -> named.bytes }
        .then { bytes, _ -> "${bytes.data.decodeToString()}@ok" }
        .build()

    val out = pipeline.run(NamedBytes("x", "hi".encodeToByteArray().asBytes()), ctx)
    assertEquals("hi@ok", out)
  }

  @Test
  fun `encode pipeline resolves output format and runs post-encode handlers`() = runTest {
    val ctx = PipelineContext(logger = TransmuteLogger.Noop)

    val pipeline: EncodePipeline<String, Bytes> =
      PipelineBuilder.start<String>()
        .startWith { ir, _ -> ir + "-pre" }
        .then { ir, _ -> ir.encodeToByteArray().asBytes() }
        .then { bytes, _ -> (bytes.data + byteArrayOf('!'.code.toByte())).asBytes() }
        .build()

    val bytes = pipeline.run("hi", ctx)
    assertEquals("hi-pre!", bytes.data.decodeToString())
  }

  @Test
  fun `pipeline builder supports branching without shared mutation`() = runTest {
    val ctx = PipelineContext(logger = TransmuteLogger.Noop)

    val base = PipelineBuilder.start<String>() startWith { s, _ -> s + "X" }

    val left = (base then { s, _ -> s + "L" }).build()
    val right = (base then { s, _ -> s + "R" }).build()

    assertEquals("hiXL", left.run("hi", ctx))
    assertEquals("hiXR", right.run("hi", ctx))
  }

  @Test
  fun `round trip decode transforms encode preserves format and order`() = runTest {
    val ctx = PipelineContext(logger = TransmuteLogger.Noop)

    val decode: DecodePipeline<Bytes, Decoded<TestFormat, String>> =
      PipelineBuilder.start<Bytes>()
        .startWith { bytes, _ -> bytes.data.decodeToString() }
        .then { ir, _ -> Decoded(TestFormat, ir) }
        .build()

    val transforms = TransformPipeline<String>().apply {
      add(object : Transform<String> {
        override val id: TransformId = TransformId("t1")
        override suspend fun apply(ir: String, context: PipelineContext): String = "($ir)"
      })
      add(object : Transform<String> {
        override val id: TransformId = TransformId("t2")
        override suspend fun apply(ir: String, context: PipelineContext): String = "$ir!"
      })
    }.transforms

    val encode: EncodePipeline<Decoded<TestFormat, String>, EncodedBytes<TestFormat>> =
      PipelineBuilder.start<Decoded<TestFormat, String>>()
        .startWith { decoded, _ ->
          EncodedBytes(decoded.format, decoded.ir.encodeToByteArray().asBytes())
        }
        .build()

    val decoded = decode.run("hi".encodeToByteArray().asBytes(), ctx)
    var ir = decoded.ir
    for (t in transforms) ir = t.apply(ir, ctx)
    val encoded = encode.run(Decoded(decoded.format, ir), ctx)

    assertEquals(TestFormat, encoded.format)
    assertEquals("(hi)!".encodeToByteArray().decodeToString(), encoded.bytes.data.decodeToString())
  }
}
