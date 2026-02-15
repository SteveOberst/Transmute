package dev.transmute.core.pipeline

import dev.transmute.core.ConversionContext
import dev.transmute.core.ConversionError
import dev.transmute.core.MediaFormat

// ── Plan ──

/** Declarative blueprint for a single file conversion. */
data class ConversionPlan(
  val source: String,
  val sink: String,
  val sourceFormat: MediaFormat,
  val targetFormat: MediaFormat,
  val stages: List<PipelineStage>,
  val metadata: Map<String, String> = emptyMap(),
)

/** A single step in a conversion plan. */
sealed class PipelineStage {
  abstract val stageId: StageId

  data class Decode(
    override val stageId: StageId,
    val decoderId: CodecId,
    val config: Map<String, Any> = emptyMap(),
  ) : PipelineStage()

  data class Transform(
    override val stageId: StageId,
    val transformId: TransformId,
    val config: Map<String, Any> = emptyMap(),
  ) : PipelineStage()

  data class Encode(
    override val stageId: StageId,
    val encoderId: CodecId,
    val config: Map<String, Any> = emptyMap(),
  ) : PipelineStage()
}

// ── Executor ──

/** Executes a [ConversionPlan] stage-by-stage. */
interface PipelineExecutor {
  suspend fun execute(plan: ConversionPlan, context: ConversionContext): Result<Unit>
}

/** Reference implementation — walks stages, delegates to registries. */
class DefaultPipelineExecutor(
  private val transformRegistry: TransformRegistry,
) : PipelineExecutor {

  override suspend fun execute(plan: ConversionPlan, context: ConversionContext): Result<Unit> {
    context.logger.info("Executing conversion plan: ${plan.source} → ${plan.sink}")
    try {
      for (stage in plan.stages) {
        context.checkCancellation()
        when (stage) {
          is PipelineStage.Decode -> context.logger.debug("Stage [${stage.stageId}]: Decode with ${stage.decoderId}")
          is PipelineStage.Transform -> context.logger.debug("Stage [${stage.stageId}]: Transform with ${stage.transformId}")
          is PipelineStage.Encode -> context.logger.debug("Stage [${stage.stageId}]: Encode with ${stage.encoderId}")
        }
      }
      context.logger.info("Conversion plan executed successfully")
      return Result.success(Unit)
    } catch (e: ConversionError.Cancelled) {
      context.logger.info("Conversion cancelled")
      throw e
    } catch (e: ConversionError) {
      context.logger.error("Conversion failed: ${e.message}")
      return Result.failure(e)
    } catch (e: Exception) {
      val wrapped = ConversionError.StageFailed(stageId = StageId("unknown"), cause = e)
      context.logger.error("Unexpected error during conversion: ${e.message}")
      return Result.failure(wrapped)
    }
  }
}

// ── Generic interfaces ──

/** IR-agnostic transform applied during the pipeline. */
interface Transform {
  /** Unique identifier used by [TransformRegistry] look-ups. */
  val id: TransformId
  suspend fun apply(ir: Any, context: ConversionContext): Any
}

/** Lookup for registered [Transform] instances. */
interface TransformRegistry {
  fun getTransform(transformId: TransformId): Transform?
}
