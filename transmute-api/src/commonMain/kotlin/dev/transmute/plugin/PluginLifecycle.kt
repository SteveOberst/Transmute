package dev.transmute.plugin

/**
 * Optional lifecycle callbacks for plugins that need post-install
 * initialization or cleanup.
 *
 * Implement this interface on your [TransmutePlugin] object to receive
 * callbacks at key lifecycle points:
 *
 * ```kotlin
 * object MyPlugin : TransmutePlugin<MyConfig>, PluginLifecycle {
 *     override val key = "com.example.my-plugin"
 *     override fun createConfig() = MyConfig()
 *
 *     override fun install(scope: TransmuteScope, config: MyConfig) {
 *         // Normal plugin installation...
 *     }
 *
 *     override fun onInstalled(scope: TransmuteScope) {
 *         // Called after ALL plugins have been installed.
 *         // Use this for cross-plugin initialization that depends on
 *         // services registered by other plugins.
 *         val cache = scope.services.getOrNull(CachePlugin.CACHE_KEY)
 *         cache?.let { scope.logger.info("Cache integration active") }
 *     }
 *
 *     override fun onClose() {
 *         // Called when the Transmute instance is closed.
 *         // Release native resources, temp dirs, background threads.
 *     }
 * }
 * ```
 */
interface PluginLifecycle {
  /**
   * Called after **all** plugins have been installed.
   *
   * The [scope] is the same one used during installation, so plugins
   * can query services registered by other plugins.
   */
  fun onInstalled(scope: TransmuteScope) {}

  /**
   * Called when the owning `Transmute` instance is closed.
   *
   * Use this to release native handles, delete temp files,
   * or shut down background threads.
   */
  fun onClose() {}
}
