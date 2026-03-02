package dev.transmute.plugin

/**
 * Optional interface for plugins to receive timing and error events for every
 * encode/decode operation they perform.
 *
 * Implementing [PluginMetricsRecorder] signals to the Transmute runtime that the
 * plugin wants a [MetricsHook] callback object.  The runtime calls the hook
 * surrounding each codec invocation so that plugins can record counters, histograms,
 * or structured logs to any backend (e.g. Micrometer, Prometheus, OpenTelemetry):
 *
 * ```kotlin
 * object MyPlugin : TransmutePlugin<MyConfig>, PluginMetricsRecorder {
 *     override val key = pluginId("com.example.my-plugin")
 *
 *     private val encodeCount = AtomicLong()
 *
 *     override fun createMetricsHook(): MetricsHook = object : MetricsHook {
 *         override fun onEncode(pluginId: PluginId, format: String, durationMs: Long) {
 *             encodeCount.incrementAndGet()
 *             logger.info("[$pluginId] encoded $format in ${durationMs}ms")
 *         }
 *         override fun onError(pluginId: PluginId, format: String, error: Throwable) {
 *             logger.error("[$pluginId] error processing $format", error)
 *         }
 *     }
 * }
 * ```
 */
interface PluginMetricsRecorder {

    /**
     * Returns a metrics hook instance this plugin wants to receive callbacks on,
     * or `null` if no metrics are desired.
     *
     * Called once per [Transmute][dev.transmute.transmute] instance construction.
     * The returned hook will be invoked on the calling coroutine's thread.
     */
    fun createMetricsHook(): MetricsHook? = null
}

/**
 * Receives timing and error notifications for codec operations performed by a plugin.
 *
 * All methods have no-op default implementations so that partial instrumentation
 * is easy - override only the events you care about.
 */
interface MetricsHook {

    /**
     * Called after a successful encode operation.
     *
     * @param pluginId   Plugin that performed the encode.
     * @param format     Output format extension (e.g. `"png"`, `"mp3"`).
     * @param durationMs Wall-clock time of the encode in milliseconds.
     */
    fun onEncode(pluginId: PluginId, format: String, durationMs: Long) {}

    /**
     * Called after a successful decode operation.
     *
     * @param pluginId   Plugin that performed the decode.
     * @param format     Input format extension (e.g. `"jpeg"`, `"wav"`).
     * @param durationMs Wall-clock time of the decode in milliseconds.
     */
    fun onDecode(pluginId: PluginId, format: String, durationMs: Long) {}

    /**
     * Called when an encode or decode operation fails with an exception.
     *
     * @param pluginId Plugin that encountered the error.
     * @param format   Format being processed at the time of the error.
     * @param error    The exception that was thrown.
     */
    fun onError(pluginId: PluginId, format: String, error: Throwable) {}
}
