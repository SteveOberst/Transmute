package dev.transmute.plugin

/**
 * Strongly-typed identifier for a Transmute plugin.
 *
 * Every [TransmutePlugin] exposes a unique [PluginId] via its [TransmutePlugin.key]
 * property. This replaces arbitrary string-based identification throughout the
 * framework - diagnostics, logging, dependency declarations, and user-facing queries
 * all use the same typed identifier.
 *
 * Plugin authors declare their identifier as a constant:
 *
 * ```kotlin
 * object MyPlugin : TransmutePlugin<MyConfig> {
 *     override val key = PluginId("com.example.my-plugin")
 *     // ...
 * }
 * ```
 *
 * Users reference that id for diagnostics, dependencies, and ordering:
 *
 * ```kotlin
 * // Query diagnostics
 * val diag = transmute.diagnostics.plugin(MyPlugin.key)
 *
 * // Declare dependencies between plugins
 * object AnotherPlugin : TransmutePlugin<Unit> {
 *     override val key = PluginId("com.example.another")
 *     override val dependsOn = setOf(MyPlugin.key)
 * }
 * ```
 *
 * @property id Reverse-domain-style identifier (e.g. `"dev.transmute.gstreamer"`).
 */
open class PluginId(val id: String) {
    override fun equals(other: Any?): Boolean = other is PluginId && other.id == id
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = id
}

/** Backward-compatible alias. Prefer [PluginId]. */
typealias PluginKey = PluginId

/**
 * Convenience function to create a [PluginId] from a string.
 *
 * ```kotlin
 * override val key = pluginId("com.example.my-plugin")
 * ```
 */
fun pluginId(id: String): PluginId = PluginId(id)

/** Backward-compatible alias. Prefer [pluginId]. */
@Deprecated("Renamed to pluginId", ReplaceWith("pluginId(id)"))
fun pluginKey(id: String): PluginId = PluginId(id)
