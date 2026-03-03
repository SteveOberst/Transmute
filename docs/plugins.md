# Plugins

## Instance-based API

Transmute supports multiple isolated instances, each with its own codec registries and plugins. The `Transmute.Default` singleton uses platform defaults and no plugins; create a dedicated instance when you need extra codecs.

```kotlin
// Custom instance with plugins
val transmute = Transmute {
    plugins {
        install(GStreamerPlugin) {
            domains(MediaDomain.VIDEO or MediaDomain.AUDIO)
        }
        install(LibHeifPlugin)
    }
}

// Use like the default instance
val out = transmute.video { resize(1280, 720) }.transmute(source)

// Release plugin resources when done
transmute.close()
```

The top-level `Transmute { }` factory function is the recommended way to build an instance:

```kotlin
fun Transmute(block: Transmute.Builder.() -> Unit = {}): Transmute
```

Alternatively, use the builder explicitly:

```kotlin
val transmute = Transmute.Builder()
    .plugins { install(GStreamerPlugin) }
    .build()
```

## Default instance

`Transmute.Default` is a lazy singleton. The companion object properties (`Transmute.image`, `Transmute.codec`, etc.) all delegate to it:

```kotlin
// These are equivalent:
Transmute.image { }.transmute(source)
Transmute.Default.image { }.transmute(source)
```

## Installing plugins

```kotlin
val transmute = Transmute {
    plugins {
        // Plugin with no configuration
        install(MySimplePlugin)

        // Plugin with configuration block
        install(MyConfigurablePlugin) {
            enableFeatureX = true
            workerThreads = 4
        }
    }
}
```

## Plugin ordering

Plugins can declare ordering constraints:

```kotlin
object MyPlugin : SimpleTransmutePlugin() {
    override val key = pluginId("com.example.myplugin")

    // Hard dependency — MyPlugin requires OtherPlugin to be installed
    override val dependsOn: Set<PluginId> = setOf(OtherPlugin.key)

    // Soft ordering — prefer to run after BasePlugin if it is present
    override val installAfter: Set<PluginId> = setOf(BasePlugin.key)

    // Soft ordering — prefer to run before AnotherPlugin if it is present
    override val installBefore: Set<PluginId> = setOf(AnotherPlugin.key)
}
```

The framework resolves installation order according to all constraints and fails fast on unmet `dependsOn` requirements.

## Inspecting installed plugins

```kotlin
val transmute = Transmute { plugins { install(GStreamerPlugin) } }
transmute.installedPlugins.forEach { info ->
    println("${info.key.id}: features=${info.features.map { it.id }}")
}
```

## Plugin lifecycle

Plugins that implement `PluginLifecycle` receive lifecycle events:

```kotlin
class MyPlugin : TransmutePlugin<Unit>, PluginLifecycle {
    override val key = pluginId("com.example.myplugin")
    override fun createConfig() = Unit
    override fun install(scope: TransmuteScope, config: Unit) { /* register codecs */ }

    override fun onInstalled() {
        // Called after all plugins have been installed
    }

    override fun onClose() {
        // Called when Transmute.close() is invoked — release native resources here
    }
}
```

Always call `transmute.close()` when an instance is no longer needed if any installed plugins implement `onClose`.

## Services

Plugins can share typed services with each other through the `ServiceRegistry`:

```kotlin
// Provider plugin registers a service
class ProviderPlugin : SimpleTransmutePlugin() {
    override val key = pluginId("com.example.provider")
    override fun install(scope: TransmuteScope) {
        scope.services.put(MyService.Key, MyService())
    }
}

// Consumer plugin retrieves it
class ConsumerPlugin : SimpleTransmutePlugin() {
    override val key = pluginId("com.example.consumer")
    override val dependsOn = setOf(ProviderPlugin.key)
    override fun install(scope: TransmuteScope) {
        val service = scope.services.get(MyService.Key)
        // use service
    }
}
```

## Diagnostics

Each plugin has a `PluginDiagnostics` channel in its scope for reporting health information:

```kotlin
override fun install(scope: TransmuteScope, config: C) {
    val available = checkNativeLibrary()
    scope.diagnostics.report(
        PluginDiagnosticEntry(
            key = "native-library",
            status = if (available) HealthStatus.OK else HealthStatus.WARN,
            message = if (available) "libfoo found" else "libfoo not found — falling back to pure-Kotlin"
        )
    )
}

// Inspect after build:
transmute.diagnostics.all.forEach { entry ->
    println("${entry.pluginKey}: ${entry.status} — ${entry.message}")
}
```
