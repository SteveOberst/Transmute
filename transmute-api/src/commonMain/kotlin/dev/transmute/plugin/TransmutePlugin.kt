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
   * Human-readable display name for this plugin (e.g. "GStreamer", "LibHeif").
   *
   * Defaults to the last segment of [key] with an upper-cased first letter.
   */
  val displayName: String get() = key.id.substringAfterLast('.').replaceFirstChar { it.uppercase() }

  /**
   * Short description of what this plugin provides.
   *
   * Shown in playground UIs and diagnostic output.  Defaults to a generic
   * string based on [key].
   */
  val description: String get() = "Transmute plugin: ${key.id}"

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
   * Unlike [dependsOn], these are soft ordering hints - the referenced
   * plugins are not required to be present.
   */
  val installAfter: Set<PluginId> get() = emptySet()

  /**
   * Plugin ids this plugin should be installed **before**.
   *
   * Soft ordering hint - the referenced plugins are not required to be present.
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
 *         scope.codecs.image.decoders.register(MyDecoder())
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
