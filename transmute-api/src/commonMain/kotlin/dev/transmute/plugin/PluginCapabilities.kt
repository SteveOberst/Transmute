package dev.transmute.plugin

/**
 * Describes the resources a plugin contributes to a [Transmute][dev.transmute.transmute]
 * instance.
 *
 * Plugins that implement [PluginCapabilityDeclaration] advertise their capabilities so
 * that tooling, playground UIs, and documentation generators can display exactly what
 * each plugin adds without needing to inspect the codec registries at runtime.
 *
 * The companion function [PluginCapabilities.of] provides a convenient builder:
 *
 * ```kotlin
 * object MyPlugin : TransmutePlugin<MyConfig>, PluginCapabilityDeclaration {
 *     override val key = pluginId("com.example.my-plugin")
 *
 *     override val declaredCapabilities = PluginCapabilities.of(key) {
 *         formats("heic", "heif", "avif")
 *         domains("image")
 *         transforms("image.ai-upscale")
 *     }
 * }
 * ```
 *
 * @property pluginId   The owning plugin's unique identifier.
 * @property formats    MIME-type extension labels of formats this plugin adds (e.g. `"heic"`).
 * @property domains    Media domains contributed (`"image"`, `"audio"`, `"video"`).
 * @property transforms Transform IDs contributed (matching [dev.transmute.TransformDescriptor.id]).
 * @property description Optional human-readable summary of what the plugin provides.
 */
data class PluginCapabilities(
  val pluginId: PluginId,
  val formats: Set<String> = emptySet(),
  val domains: Set<String> = emptySet(),
  val transforms: Set<String> = emptySet(),
  val description: String = "",
) {
  companion object {
    /** DSL builder for [PluginCapabilities]. */
    fun of(pluginId: PluginId, block: Builder.() -> Unit): PluginCapabilities = Builder(pluginId).apply(block).build()
  }

  class Builder(private val pluginId: PluginId) {
    private val formats = mutableSetOf<String>()
    private val domains = mutableSetOf<String>()
    private val transforms = mutableSetOf<String>()
    private var description = ""

    fun formats(vararg names: String) {
      formats += names
    }
    fun domains(vararg names: String) {
      domains += names
    }
    fun transforms(vararg ids: String) {
      transforms += ids
    }
    fun description(text: String) {
      description = text
    }

    fun build() = PluginCapabilities(
      pluginId = pluginId,
      formats = formats.toSet(),
      domains = domains.toSet(),
      transforms = transforms.toSet(),
      description = description,
    )
  }
}

/**
 * Optional interface for plugins to declare their capabilities up-front, without
 * requiring the full codec registries to be built.
 *
 * Used by `transmute.plugins.listCapabilities()` to provide a summary of all
 * known plugins and what each one contributes.
 */
interface PluginCapabilityDeclaration {
  /**
   * Static declaration of what this plugin provides.
   *
   * Must be computable without side-effects and without an active [TransmuteScope].
   */
  val declaredCapabilities: PluginCapabilities
}
