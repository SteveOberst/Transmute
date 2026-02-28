package dev.transmute.plugin

/**
 * Read-only metadata about an installed plugin.
 *
 * Returned by [Transmute.installedPlugins][dev.transmute.Transmute.installedPlugins]
 * so consumers (e.g. the Playground backend) can enumerate installed plugins
 * without depending on the concrete plugin classes.
 */
data class InstalledPluginInfo(
    /** Unique identifier for this plugin. */
    val key: PluginId,
    /** Features this plugin declares (typed toggles). */
    val features: Set<PluginFeature> = emptySet(),
    /** Hard dependencies on other plugins. */
    val dependsOn: Set<PluginId> = emptySet(),
)
