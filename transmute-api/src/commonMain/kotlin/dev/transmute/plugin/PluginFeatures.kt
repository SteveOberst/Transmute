package dev.transmute.plugin

/**
 * Named feature toggles for a plugin.
 *
 * Plugin configurations can declare named features that users enable or
 * disable. This provides a standardized way to toggle plugin capabilities
 * without requiring per-feature config properties.
 *
 * **Prefer the typed [PluginFeature] overloads** - they provide compile-time
 * safety, self-documenting feature catalogs, and correct default values.
 *
 * Features are configured via the `configure { features { } }` DSL:
 *
 * ```kotlin
 * install(GStreamer) {
 *     configure {
 *         features {
 *             enable(GStreamerFeature.AudioCodecs)
 *             disable(GStreamerFeature.LegacyAvi)
 *         }
 *     }
 * }
 * ```
 *
 * Plugin code checks features at install time:
 *
 * ```kotlin
 * override fun install(scope: TransmuteScope, config: MyConfig) {
 *     if (scope.features.isEnabled(MyFeature.FastEncode)) {
 *         scope.codecs.image.encoders.register(HwAccelEncoder())
 *     }
 * }
 * ```
 */
class PluginFeaturesConfig constructor() {
  private val _overrides = mutableMapOf<String, Boolean>()

  /** All user-specified feature overrides (feature-id -> enabled). */
  val overrides: Map<String, Boolean> get() = _overrides.toMap()

  // -- Typed PluginFeature API (preferred) ---

  /** Explicitly enable a [PluginFeature]. */
  fun enable(feature: PluginFeature) {
    _overrides[feature.id] = true
  }

  /** Explicitly disable a [PluginFeature]. */
  fun disable(feature: PluginFeature) {
    _overrides[feature.id] = false
  }

  /** Set a [PluginFeature] to the given enabled/disabled state. */
  fun set(feature: PluginFeature, enabled: Boolean) {
    _overrides[feature.id] = enabled
  }

  /**
   * Check if a [PluginFeature] is enabled.
   *
   * Returns the user's explicit override if present, otherwise falls back
   * to [PluginFeature.defaultEnabled].
   */
  fun isEnabled(feature: PluginFeature): Boolean = _overrides[feature.id] ?: feature.defaultEnabled

  // -- String-based API (legacy / fallback) ---

  /** Explicitly enable a named feature by raw string ID. */
  fun enable(featureId: String) {
    _overrides[featureId] = true
  }

  /** Explicitly disable a named feature by raw string ID. */
  fun disable(featureId: String) {
    _overrides[featureId] = false
  }

  /** Set a feature to the given enabled/disabled state by raw string ID. */
  fun set(featureId: String, enabled: Boolean) {
    _overrides[featureId] = enabled
  }

  /**
   * Check if a feature is enabled by raw string ID.
   *
   * Returns `true` if the user explicitly enabled it, `false` if explicitly
   * disabled, or [default] if the user did not specify.
   */
  fun isEnabled(featureId: String, default: Boolean = true): Boolean = _overrides[featureId] ?: default
}
