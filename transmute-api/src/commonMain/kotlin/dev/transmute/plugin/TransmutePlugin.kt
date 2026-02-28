package dev.transmute.plugin

/**
 * Extension point for adding capabilities to a [Transmute][dev.transmute.transmute] instance.
 *
 * Plugins are installed during instance construction via the builder DSL:
 * ```kotlin
 * val transmute = Transmute {
 *     plugins {
 *         install(MyPlugin) {
 *             // plugin-specific configuration
 *         }
 *     }
 * }
 * ```
 *
 * A plugin receives a [TransmuteScope] containing the mutable registries it can
 * populate (image/audio/video decoders & encoders), a type-safe [ServiceRegistry],
 * structured [PluginDiagnostics], and [PluginFeaturesConfig] toggles.
 *
 * @param C plugin-specific configuration type built inside the `install { }` block.
 */
interface TransmutePlugin<C : Any> {
  /** Unique identifier for this plugin (reverse-domain style recommended). */
  val key: PluginId

  /**
   * Plugin ids this plugin depends on.
   *
   * The framework ensures all dependencies are installed before this plugin
   * and fails fast if a dependency is missing.
   */
  val dependsOn: Set<PluginId> get() = emptySet()

  /**
   * Plugin ids this plugin should be installed **after**.
   *
   * Unlike [dependsOn], these are soft ordering hints — the referenced
   * plugins are not required to be present.
   */
  val installAfter: Set<PluginId> get() = emptySet()

  /**
   * Plugin ids this plugin should be installed **before**.
   *
   * Soft ordering hint — the referenced plugins are not required to be present.
   */
  val installBefore: Set<PluginId> get() = emptySet()

  /**
   * Features this plugin supports.
   *
   * Returned set is used for introspection and documentation. Each feature
   * can be toggled by users via `configure { features { } }`.
   */
  val features: Set<PluginFeature> get() = emptySet()

  /** Creates a fresh default configuration for this plugin. */
  fun createConfig(): C

  /** Applies this plugin to [scope] using the given [config]. */
  fun install(scope: TransmuteScope, config: C)
}

/**
 * Convenience base for plugins that require no user configuration.
 *
 * ```kotlin
 * object MySimplePlugin : SimpleTransmutePlugin() {
 *     override val key = pluginId("my-plugin")
 *     override fun install(scope: TransmuteScope) {
 *         scope.imageDecoders.register(MyDecoder())
 *     }
 * }
 * ```
 */
abstract class SimpleTransmutePlugin : TransmutePlugin<Unit> {
  override fun createConfig() = Unit
  abstract fun install(scope: TransmuteScope)
  override fun install(scope: TransmuteScope, config: Unit) = install(scope)
}

/** Convenience extension to get the string identifier from a [PluginId]. */
val TransmutePlugin<*>.id: String get() = key.id
