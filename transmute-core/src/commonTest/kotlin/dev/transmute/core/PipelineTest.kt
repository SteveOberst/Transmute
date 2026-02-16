package dev.transmute.core

import dev.transmute.core.ConversionContext
import dev.transmute.core.ConversionLogger
import dev.transmute.core.ImageFormat
import dev.transmute.core.MetadataPolicy
import dev.transmute.core.pipeline.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PipelineTest {

  // --- helpers ---

  private fun testContext(job: Job = Job()) = ConversionContext(
    jobId = "test-job",
    metadataPolicy = MetadataPolicy.PRESERVE,
    coroutineJob = job,
    logger = object : ConversionLogger {
      override fun debug(message: String) {}
      override fun info(message: String) {}
      override fun warn(message: String) {}
      override fun error(message: String, throwable: Throwable?) {}
    },
  )

  /** A transform that appends a marker to demonstrate ordering. */
  private class MarkerTransform(override val id: TransformId) : Transform<MutableList<String>> {
    override suspend fun apply(ir: MutableList<String>, context: ConversionContext): MutableList<String> {
      ir.add(id.value)
      return ir
    }
  }

  // --- tests ---

  @Test
  fun planCreationPreservesStages() {
    val plan = ConversionPlan(
      source = "input.jpg",
      sink = "output.webp",
      sourceFormat = ImageFormat.JPEG,
      targetFormat = ImageFormat.WEBP,
      stages = listOf(
        PipelineStage.Decode(stageId = StageId("decode"), decoderId = CodecId("jpeg")),
        PipelineStage.Transform(stageId = StageId("resize"), transformId = TransformId("resize")),
        PipelineStage.Encode(stageId = StageId("encode"), encoderId = CodecId("webp")),
      ),
    )
    assertEquals(3, plan.stages.size)
    assertTrue(plan.stages[0] is PipelineStage.Decode)
    assertTrue(plan.stages[1] is PipelineStage.Transform)
    assertTrue(plan.stages[2] is PipelineStage.Encode)
  }

  @Test
  fun executorWalksStagesSuccessfully() = runTest {
    val registry = object : TransformRegistry {
      override fun getTransform(transformId: TransformId): Transform<*>? = null
    }
    val executor = DefaultPipelineExecutor(registry)
    val ctx = testContext()

    val plan = ConversionPlan(
      source = "input.jpg",
      sink = "output.webp",
      sourceFormat = ImageFormat.JPEG,
      targetFormat = ImageFormat.WEBP,
      stages = listOf(
        PipelineStage.Decode(stageId = StageId("decode"), decoderId = CodecId("jpeg")),
        PipelineStage.Transform(stageId = StageId("scale"), transformId = TransformId("image-scale")),
        PipelineStage.Encode(stageId = StageId("encode"), encoderId = CodecId("webp")),
      ),
    )

    val result = executor.execute(plan, ctx)
    assertTrue(result.isSuccess)
  }

  @Test
  fun contextCarriesMetadataPolicy() {
    val ctx = testContext()
    assertEquals(MetadataPolicy.PRESERVE, ctx.metadataPolicy)
  }

  @Test
  fun planMetadataIsPreserved() {
    val plan = ConversionPlan(
      source = "input.jpg",
      sink = "output.webp",
      sourceFormat = ImageFormat.JPEG,
      targetFormat = ImageFormat.WEBP,
      stages = emptyList(),
      metadata = mapOf("profile" to "balanced", "quality" to "85"),
    )
    assertEquals("balanced", plan.metadata["profile"])
    assertEquals("85", plan.metadata["quality"])
  }
}
