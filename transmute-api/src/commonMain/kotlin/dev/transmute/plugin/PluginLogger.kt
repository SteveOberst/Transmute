package dev.transmute.plugin

import dev.transmute.common.LogLevel
import dev.transmute.common.TransmuteLogger

/**
 * Per-plugin logger that tags log messages with the plugin's key.
 *
 * Every plugin gets its own [PluginLogger] automatically when installed via
 * [TransmutePlugin.install]. The logger delegates to a [TransmuteLogger]
 * backend and prepends the plugin key to each message.
 *
 * ```kotlin
 * // Inside a TransmutePlugin.install():
 * scope.logger.info("Registering audio codecs")
 * // Output: [my-plugin] Registering audio codecs
 * ```
 */
class PluginLogger internal constructor(
  /** The plugin id used as prefix for log messages. */
  val key: PluginId,
  private var delegate: TransmuteLogger = TransmuteLogger.Noop,
  private var minLevel: LogLevel = LogLevel.WARN,
) : TransmuteLogger {

  override fun debug(message: String) {
    if (minLevel <= LogLevel.DEBUG) delegate.debug("[$key] $message")
  }

  override fun info(message: String) {
    if (minLevel <= LogLevel.INFO) delegate.info("[$key] $message")
  }

  override fun warn(message: String) {
    if (minLevel <= LogLevel.WARN) delegate.warn("[$key] $message")
  }

  override fun error(message: String, throwable: Throwable?) {
    if (minLevel <= LogLevel.ERROR) delegate.error("[$key] $message", throwable)
  }

  /**
   * Reconfigure this plugin logger.
   *
   * Called by the framework when the user supplies a `configure { }` block.
   */
  internal fun applyConfig(config: PluginLoggerConfig) {
    config.levelOverride?.let { minLevel = it }
    config.backendOverride?.let { delegate = it }
  }
}

/**
 * Configuration for a plugin's logger, built inside a `configure { logging { } }` block.
 *
 * ```kotlin
 * install(GStreamer) {
 *     configure {
 *         logging {
 *             level(LogLevel.DEBUG)
 *             backend(myCustomLogger)
 *         }
 *     }
 * }
 * ```
 */
class PluginLoggerConfig internal constructor() {
  internal var levelOverride: LogLevel? = null
  internal var backendOverride: TransmuteLogger? = null

  /** Set the minimum log level for this plugin. */
  fun level(level: LogLevel) {
    levelOverride = level
  }

  /** Set a custom logger backend for this plugin. */
  fun backend(logger: TransmuteLogger) {
    backendOverride = logger
  }
}
