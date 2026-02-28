package dev.transmute.plugin

import dev.transmute.common.PrintLogger

/**
 * Type-erased wrapper that captures a plugin + its configuration lambda
 * so they can be stored in a list and applied later during build.
 *
 * Each installation carries a [PluginLogger] scoped to the plugin's key
 * and an optional [PluginConfigure] block for cross-cutting concerns.
 */
internal class PluginInstallation<C : Any>(
  val plugin: TransmutePlugin<C>,
  private val configure: C.() -> Unit,
) {
  /** The logger for this plugin, created once and configured during [apply]. */
  private val pluginLogger = PluginLogger(plugin.key, delegate = PrintLogger)

  /** The scope this plugin was installed into (set after [apply]). */
  private var installedScope: TransmuteScope? = null

  fun apply(scope: TransmuteScope, aggregateDiagnostics: AggregateDiagnostics) {
    val config = plugin.createConfig().apply(configure)

    // Extract PluginConfigure if the config exposes one
    val pluginConfigure = (config as? HasPluginConfigure)?.pluginConfigure
    pluginConfigure?.let { pluginLogger.applyConfig(it.loggerConfig) }

    val pluginDiag = aggregateDiagnostics.forPlugin(plugin.key)
    val featuresConfig = pluginConfigure?.featuresConfig ?: PluginFeaturesConfig()

    // Create a scope with this plugin's logger, diagnostics, and features
    val pluginScope = TransmuteScope(
      codecs = scope.codecs,
      services = scope.services,
      diagnostics = pluginDiag,
      logger = pluginLogger,
      features = featuresConfig,
      mediaStructures = scope.mediaStructures,
    )
    installedScope = pluginScope
    plugin.install(pluginScope, config)
  }

  /** Fire [PluginLifecycle.onInstalled] if the plugin implements it. */
  fun fireOnInstalled() {
    val lifecycle = plugin as? PluginLifecycle ?: return
    val scope = installedScope ?: return
    lifecycle.onInstalled(scope)
  }

  /** Fire [PluginLifecycle.onClose] if the plugin implements it. */
  fun fireOnClose() {
    (plugin as? PluginLifecycle)?.onClose()
  }

  val key: PluginId get() = plugin.key
}

/**
 * Marker interface for plugin config classes that support the `configure { }` block.
 *
 * Implement this in your plugin's configuration class to gain access to
 * cross-cutting concerns like per-plugin logging and feature toggles:
 *
 * ```kotlin
 * class MyPluginConfig : HasPluginConfigure {
 *     override val pluginConfigure = PluginConfigure()
 *
 *     fun configure(block: PluginConfigure.() -> Unit) {
 *         pluginConfigure.apply(block)
 *     }
 * }
 * ```
 */
interface HasPluginConfigure {
  val pluginConfigure: PluginConfigure
}

/**
 * Topologically sort plugin installations respecting [TransmutePlugin.dependsOn],
 * [TransmutePlugin.installAfter], and [TransmutePlugin.installBefore].
 *
 * Fails fast on missing hard dependencies or cycles.
 */
internal fun sortPluginInstallations(
    installations: List<PluginInstallation<*>>,
): List<PluginInstallation<*>> {
    if (installations.size <= 1) return installations

    val byKey = installations.associateBy { it.key }
    val keys = byKey.keys

    // Validate hard dependencies
    for (inst in installations) {
        for (dep in inst.plugin.dependsOn) {
            require(dep in keys) {
                "Plugin '${inst.key}' depends on '$dep', which is not installed"
            }
        }
    }

    // Build adjacency: edges[a] = set of b means "a must be installed before b"
    val edges = mutableMapOf<PluginKey, MutableSet<PluginKey>>()
    val inDegree = mutableMapOf<PluginKey, Int>()
    for (k in keys) {
        edges[k] = mutableSetOf()
        inDegree[k] = 0
    }

    fun addEdge(before: PluginKey, after: PluginKey) {
        if (before == after) return
        if (before !in keys || after !in keys) return
        if (edges[before]!!.add(after)) {
            inDegree[after] = inDegree[after]!! + 1
        }
    }

    for (inst in installations) {
        // Hard dependencies: dep must install before this
        for (dep in inst.plugin.dependsOn) addEdge(dep, inst.key)
        // Soft ordering: install after these
        for (after in inst.plugin.installAfter) addEdge(after, inst.key)
        // Soft ordering: install before these
        for (before in inst.plugin.installBefore) addEdge(inst.key, before)
    }

    // Kahn's algorithm for topological sort
    val queue = ArrayDeque<PluginKey>()
    for ((k, deg) in inDegree) { if (deg == 0) queue.add(k) }

    val sorted = mutableListOf<PluginKey>()
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        sorted.add(current)
        for (neighbor in edges[current]!!) {
            inDegree[neighbor] = inDegree[neighbor]!! - 1
            if (inDegree[neighbor] == 0) queue.add(neighbor)
        }
    }

    require(sorted.size == keys.size) {
        val remaining = keys - sorted.toSet()
        "Cyclic plugin dependency detected involving: $remaining"
    }

    return sorted.map { byKey[it]!! }
}
