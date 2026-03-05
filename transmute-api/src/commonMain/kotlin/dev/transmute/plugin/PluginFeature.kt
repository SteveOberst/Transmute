package dev.transmute.plugin

/**
 * Strongly-typed identifier for a named feature within a plugin.
 *
 * Plugins declare their toggleable features as [PluginFeature] constants,
 * providing compile-time safety and self-documenting feature catalogs.
 * Users reference these constants to enable or disable specific capabilities.
 *
 * ```kotlin
 * // Plugin author declares features:
 * object MyFeatures {
 *     val FAST_ENCODE = PluginFeature(
 *         id = "fast-encode",
 *         description = "Use hardware-accelerated encoding when available",
 *         defaultEnabled = true,
 *     )
 *     val LEGACY_FORMAT = PluginFeature(
 *         id = "legacy-format",
 *         description = "Support for deprecated legacy container format",
 *         defaultEnabled = false,
 *     )
 * }
 *
 * // Users toggle features via the DSL:
 * install(MyPlugin) {
 *     configure {
 *         features {
 *             disable(MyFeatures.FAST_ENCODE)
 *             enable(MyFeatures.LEGACY_FORMAT)
 *         }
 *     }
 * }
 * ```
 *
 * Plugin code checks feature state during installation:
 *
 * ```kotlin
 * override fun install(scope: TransmuteScope, config: MyConfig) {
 *     if (scope.features.isEnabled(MyFeatures.FAST_ENCODE)) {
 *         scope.codecs.image.encoders.register(HwAccelEncoder())
 *     }
 * }
 * ```
 *
 * @property id Unique identifier for this feature within its plugin
 *   (e.g. `"audio-codecs"`, `"heif-encode"`).
 * @property description Human-readable description of what this feature controls.
 * @property defaultEnabled Whether the feature is enabled when the user does not
 *   explicitly set it. Defaults to `true`.
 */
open class PluginFeature(val id: String, val description: String = "", val defaultEnabled: Boolean = true) {
  override fun equals(other: Any?): Boolean = other is PluginFeature && other.id == id
  override fun hashCode(): Int = id.hashCode()
  override fun toString(): String = id
}
