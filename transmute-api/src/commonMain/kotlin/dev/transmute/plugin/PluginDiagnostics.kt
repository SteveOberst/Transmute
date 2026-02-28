package dev.transmute.plugin

/**
 * Structured diagnostic information reported by a plugin.
 *
 * Provides a unified way for plugins to expose health/status
 * information instead of ad-hoc boolean flags or stderr output.
 *
 * ```kotlin
 * // Report availability status:
 * scope.diagnostics.report(PluginStatus(
 *     available = true,
 *     reason = "GStreamer 1.24.0 resolved from bundled extraction",
 *     details = mapOf(
 *         "gst-launch" to "/home/user/.transmute/gstreamer/1.24.0/bin/gst-launch-1.0",
 *         "version" to "1.24.0",
 *     ),
 * ))
 * ```
 */
data class PluginStatus(
    /** Whether the plugin's core functionality is available. */
    val available: Boolean,
    /** Human-readable reason for the current state. */
    val reason: String,
    /** Additional key-value details for debugging. */
    val details: Map<String, String> = emptyMap(),
)

/**
 * Diagnostics collector for a single plugin.
 *
 * Each plugin gets its own [PluginDiagnostics] instance via `scope.diagnostics`.
 * Reported statuses are collected by the framework and can be queried after build.
 */
class PluginDiagnostics internal constructor(
    /** The plugin id this diagnostics instance belongs to. */
    val key: PluginId,
) {
    private val _statuses = mutableListOf<PluginStatus>()

    /** All statuses reported by this plugin. */
    val statuses: List<PluginStatus> get() = _statuses.toList()

    /** The most recent status, or `null` if none reported. */
    val current: PluginStatus? get() = _statuses.lastOrNull()

    /** Report a status from this plugin. */
    fun report(status: PluginStatus) {
        _statuses.add(status)
    }

    /** Convenience: report availability with a reason string. */
    fun report(available: Boolean, reason: String, details: Map<String, String> = emptyMap()) {
        report(PluginStatus(available, reason, details))
    }
}

/**
 * Aggregated diagnostics from all installed plugins.
 *
 * Available after the `Transmute` instance is built. Access via
 * `transmute.diagnostics` (future — once exposed on the Transmute class).
 */
class AggregateDiagnostics internal constructor() {
    private val _plugins = mutableMapOf<PluginId, PluginDiagnostics>()

    internal fun forPlugin(key: PluginId): PluginDiagnostics =
        _plugins.getOrPut(key) { PluginDiagnostics(key) }

    /** Diagnostics for a specific plugin, or `null` if it didn't report anything. */
    fun plugin(key: PluginId): PluginDiagnostics? = _plugins[key]

    /** All plugin diagnostics. */
    fun all(): Map<PluginId, PluginDiagnostics> = _plugins.toMap()

    /** Quick summary: all plugins and whether they're available. */
    fun summary(): Map<PluginId, Boolean> =
        _plugins.mapValues { (_, diag) -> diag.current?.available ?: true }
}
