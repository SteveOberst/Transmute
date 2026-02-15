package dev.transmute.core

import dev.transmute.core.pipeline.StageId
import dev.transmute.core.pipeline.TransformId

/** Exhaustive error hierarchy for every failure the pipeline can produce. */
sealed class ConversionError(message: String, cause: Throwable? = null) : Exception(message, cause) {

  data class UnsupportedFormat(val format: MediaFormat?) :
    ConversionError("Unsupported format: ${format?.mimeType ?: "unknown"}")

  data class DecoderNotFound(val format: MediaFormat) :
    ConversionError("No decoder registered for format: ${format.mimeType}")

  data class EncoderNotFound(val format: MediaFormat) :
    ConversionError("No encoder registered for format: ${format.mimeType}")

  data class TransformNotFound(val transformId: TransformId) :
    ConversionError("No transform registered for ID: $transformId")

  data class InvalidPlan(val reason: String) :
    ConversionError("Invalid conversion plan: $reason")

  data class StageFailed(val stageId: StageId, override val cause: Throwable) :
    ConversionError("Stage '$stageId' failed", cause)

  object Cancelled : ConversionError("Conversion cancelled")

  data class SourceNotFound(val path: String) :
    ConversionError("Source file not found: $path")

  data class SinkWriteFailed(val path: String, override val cause: Throwable) :
    ConversionError("Failed to write output: $path", cause)
}
