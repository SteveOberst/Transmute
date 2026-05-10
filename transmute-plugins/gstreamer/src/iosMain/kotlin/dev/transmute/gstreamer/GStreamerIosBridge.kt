@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package dev.transmute.gstreamer

/**
 * Bridge to GStreamer on iOS via Kotlin/Native cinterop.
 *
 * GStreamer.framework must be linked into the final app binary for
 * [available] to return `true`. If the framework is not present,
 * all operations gracefully report unavailability.
 *
 * The cinterop-backed implementation lives in the concrete iOS target
 * source sets so shared iosMain metadata does not depend on target-only
 * bindings during publication.
 */
internal expect object GStreamerIosBridge {
  val available: Boolean

  fun hasElement(name: String): Boolean

  fun runPipeline(pipelineDesc: String): Boolean

  fun runPipelineChecked(args: List<String>)
}
