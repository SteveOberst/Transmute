package dev.transmute.core.pipeline

import dev.transmute.core.TransmuteContext
import dev.transmute.core.TransmuteLogger
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EncodeDecodePipelineTest {

  @Test
  fun `decode pipeline runs byte then ir handlers`() = runTest {
    val ctx = TransmuteContext(logger = TransmuteLogger.Noop)

    val pipeline: DecodePipeline<ByteArray, String> = PipelineBuilder.start<ByteArray>()
      .then { bytes, _ -> bytes + byteArrayOf('!'.code.toByte()) }
      .then { bytes, _ -> bytes.decodeToString() }
      .then { ir, _ -> ir + "-ir" }
      .build()

    val out = pipeline.run("hi".encodeToByteArray(), ctx)
    assertEquals("hi!-ir", out)
  }

  @Test
  fun `encode pipeline resolves output format and runs post-encode handlers`() = runTest {
    val ctx = TransmuteContext(logger = TransmuteLogger.Noop)

    val pipeline: EncodePipeline<String, ByteArray> =
      PipelineBuilder.start<String>()
        .then { ir, _ -> ir + "-pre" }
        .then { ir, _ -> ir.encodeToByteArray() }
        .then { bytes, _ -> bytes + byteArrayOf('!'.code.toByte()) }
        .build()

    val bytes = pipeline.run("hi", ctx)
    assertEquals("hi-pre!", bytes.decodeToString())
  }
}
