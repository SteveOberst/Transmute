package dev.transmute.plugin

/**
 * Optional contract for plugins to perform early validation of their
 * user-supplied configuration.
 *
 * Implement this interface on your [TransmutePlugin] object to receive a
 * validation call before [TransmutePlugin.install] is invoked.  The framework
 * collects all reported problems and throws [PluginConfigException] when any
 * error-severity problems are present, giving users actionable feedback:
 *
 * ```kotlin
 * object MyPlugin : TransmutePlugin<MyConfig>, PluginConfigValidation<MyConfig> {
 *     override val key = pluginId("com.example.my-plugin")
 *
 *     override fun validate(config: MyConfig): List<ConfigProblem> = buildList {
 *         if (config.executablePath.isBlank()) {
 *             add(ConfigProblem("executablePath", "Path to native binary must not be blank"))
 *         }
 *         if (config.maxThreads < 1) {
 *             add(ConfigProblem("maxThreads", "Must be >= 1", ConfigProblem.Severity.WARNING))
 *         }
 *     }
 * }
 * ```
 *
 * @param C Plugin configuration type - the same `C` used in [TransmutePlugin].
 */
interface PluginConfigValidation<C : Any> {

  /**
   * Validates [config] and returns a (possibly empty) list of problems.
   *
   * - [ConfigProblem.Severity.ERROR]: the framework will abort installation.
   * - [ConfigProblem.Severity.WARNING]: the framework logs and continues.
   * - [ConfigProblem.Severity.INFO]: informational; always logged.
   */
  fun validate(config: C): List<ConfigProblem> = emptyList()
}

/**
 * A single validation problem found in a plugin's configuration.
 *
 * @property field    The configuration property name (or dot-path for nested fields).
 * @property message  Human-readable description of the problem.
 * @property severity How the framework should react to this problem.
 */
data class ConfigProblem(val field: String, val message: String, val severity: Severity = Severity.ERROR) {
  enum class Severity { ERROR, WARNING, INFO }
}

/**
 * Thrown by the Transmute framework when one or more [ConfigProblem.Severity.ERROR]-level
 * problems are reported during plugin config validation.
 */
class PluginConfigException(pluginId: PluginId, val problems: List<ConfigProblem>) :
  IllegalArgumentException(
    "Plugin '${pluginId.id}' configuration is invalid:\n" +
      problems.joinToString("\n") { "  [${it.severity}] ${it.field}: ${it.message}" },
  )
