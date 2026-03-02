package dev.transmute.plugin

/**
 * Controls the priority with which a plugin's codecs are selected over other
 * registered codecs that handle the same format.
 *
 * When multiple plugins register decoders or encoders for the same format, the
 * Transmute runtime uses priority ordering to pick the "best" one.  Plugins
 * implement [PluginRegistryPriority] to declare their intended position:
 *
 * ```kotlin
 * // A HW-accelerated GPU plugin that should be tried before the CPU fallback.
 * object GpuAccelPlugin : TransmutePlugin<GpuConfig>, PluginRegistryPriority {
 *     override val key = pluginId("com.example.gpu-accel")
 *     override val registryPriority = RegistryPriority.PREFERRED
 * }
 *
 * // A pure-software reference codec that should only be used as a last resort.
 * object ReferenceCodecPlugin : TransmutePlugin<Unit>, PluginRegistryPriority {
 *     override val key = pluginId("com.example.reference-codec")
 *     override val registryPriority = RegistryPriority.FALLBACK
 * }
 * ```
 *
 * Plugins that do not implement this interface are treated as [RegistryPriority.DEFAULT].
 */
interface PluginRegistryPriority {
    /**
     * The priority tier this plugin's codecs occupy in the codec registry.
     *
     * Defaults to [RegistryPriority.DEFAULT].
     */
    val registryPriority: RegistryPriority get() = RegistryPriority.DEFAULT
}

/**
 * Describes where a plugin's codecs sit in the registry priority order.
 *
 * Resolution order (highest first): [PREFERRED] -> [DEFAULT] -> [FALLBACK].
 */
enum class RegistryPriority {

    /**
     * This plugin's codecs are preferred and will be tried **before** any
     * [DEFAULT] or [FALLBACK] codecs for the same format.
     *
     * Typical use: hardware-accelerated or platform-native codecs.
     */
    PREFERRED,

    /**
     * Standard priority - the codec is registered at the position determined
     * by installation order within the same tier.
     *
     * This is the default for all plugins that do not implement [PluginRegistryPriority].
     */
    DEFAULT,

    /**
     * This plugin's codecs are used only when no [PREFERRED] or [DEFAULT] codec
     * can handle the format.
     *
     * Typical use: pure-software reference implementations, slow-but-compatible fallbacks.
     */
    FALLBACK,
}
