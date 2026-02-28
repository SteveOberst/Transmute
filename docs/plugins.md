# Instance-Based API & Plugin System

Transmute supports both a convenient static API and a fully instance-based API
where each `Transmute` instance carries its own codec registries and plugin
configuration.

## Static vs Instance API

### Static API (zero setup)

The static `Transmute.image`, `Transmute.audio`, `Transmute.video`, `Transmute.codec`,
`Transmute.inspect`, and `Transmute.structure` properties delegate to a lazy default
instance (`Transmute.Default`) that uses platform-native codecs. No setup is needed:

```kotlin
val jpeg = Transmute.image {
    scale(maxWidth = 1920, maxHeight = 1080)
}.transmute(pngBytes.asBytes())
```

### Instance API

Build a `Transmute` instance with the `Transmute { }` factory function. Each instance
gets its own codec registries populated with platform defaults, then extended by plugins:

```kotlin
// All features (audio, video, image) are enabled by default
val transmute = Transmute {
    plugins {
        install(GStreamer)
    }
}

// Use exactly like the static API
val heif = transmute.image {
    scale(maxWidth = 512, maxHeight = 512)
}.transmute(heifBytes.asBytes())
```

Multiple instances can coexist with different codec configurations:

```kotlin
val basic = Transmute()                          // platform defaults only
val full  = Transmute { plugins { install(GStreamer) } }  // + GStreamer codecs
```

## Plugin System

### Installing Plugins

Plugins are installed via the `plugins { }` block in the builder DSL:

```kotlin
val transmute = Transmute {
    plugins {
        install(PluginA)                   // no-config plugin
        install(PluginB) {                 // plugin with configuration
            someOption = true
            anotherOption = "value"
        }
    }
}
```

### Built-in Plugins

| Plugin      | Module                        | Description                                    |
|-------------|-------------------------------|------------------------------------------------|
| `GStreamer` | `transmute-plugins:gstreamer` | HEIF/AVIF, OGG/Opus/FLAC, MP4/MOV/WebM/AVI/MKV |

See the [plugin catalog](../transmute-plugins/README.md) for the full list.

### Writing a Plugin

Implement `TransmutePlugin<C>` where `C` is your configuration type.
Each plugin declares a `PluginId` — a strongly-typed identifier that replaces
raw strings throughout the framework:

```kotlin
object MyCodecPlugin : TransmutePlugin<MyCodecConfig> {
    override val key = pluginId("com.example.my-codec")
    override fun createConfig() = MyCodecConfig()

    override fun install(scope: TransmuteScope, config: MyCodecConfig) {
        if (config.enableDecoder) {
            scope.imageDecoders.register(MyDecoder())
        }
        if (config.enableEncoder) {
            scope.imageEncoders.register(MyEncoder())
        }
    }
}

class MyCodecConfig {
    var enableDecoder: Boolean = true
    var enableEncoder: Boolean = true
}
```

For plugins that don't need configuration, extend `SimpleTransmutePlugin`:

```kotlin
object MinimalPlugin : SimpleTransmutePlugin() {
    override val key = pluginId("com.example.minimal")
    override fun install(scope: TransmuteScope) {
        scope.audioDecoders.register(MyAudioDecoder())
    }
}
```

### TransmuteScope

The `TransmuteScope` passed to `install()` exposes a `CodecRegistry` for all three
media domains, a per-plugin logger, a type-safe service registry, structured diagnostics,
feature toggles, and a `MediaStructure` serialization scope:

| Property          | Type                              | Purpose                                                        |
|-------------------|-----------------------------------|----------------------------------------------------------------|
| `codecs`          | `CodecRegistry`                   | All codec registries grouped by domain (`image`, `audio`, `video`) |
| `logger`          | `PluginLogger`                    | Per-plugin logger tagged with the plugin's key                 |
| `services`        | `ServiceRegistry`                 | Type-safe cross-plugin services                                |
| `diagnostics`     | `PluginDiagnostics`               | Structured health/status reporting                             |
| `features`        | `PluginFeaturesConfig`            | User-configured feature toggles                               |
| `mediaStructures` | `MediaStructureRegistrationScope` | Register `MediaStructure` subtypes for JSON serialization      |

`CodecRegistry` groups each domain's four mutable registries:

| Access path                               | Purpose                                    |
|-------------------------------------------|--------------------------------------------|
| `scope.codecs.image.decoders`             | IR image decoders (pixels)                 |
| `scope.codecs.image.encoders`             | IR image encoders (pixels)                 |
| `scope.codecs.image.rawStructureDecoders` | Bytes → `RawMediaStructure` decoders       |
| `scope.codecs.image.structureDecoders`    | Bytes → `MediaStructure` decoders          |
| `scope.codecs.audio.*` / `scope.codecs.video.*` | Same four registries for audio/video |

### Per-Plugin Logging

Every plugin automatically receives its own `PluginLogger` via `scope.logger`.
The logger prefixes all messages with the plugin's key (e.g. `[dev.transmute.gstreamer]`)
and supports level filtering.

Plugins use the logger like any `TransmuteLogger`:

```kotlin
override fun install(scope: TransmuteScope, config: MyConfig) {
    scope.logger.info("Installing codecs…")
    scope.logger.debug("Detailed diagnostic information")
    scope.logger.warn("Something unexpected happened")
}
```

#### Configuring Plugin Logging

Users control per-plugin logging through the `configure { }` block. To enable this,
your config class must implement `HasPluginConfigure`:

```kotlin
class MyPluginConfig : HasPluginConfigure {
    override val pluginConfigure = PluginConfigure()

    // Plugin-specific settings…

    fun configure(block: PluginConfigure.() -> Unit) {
        pluginConfigure.apply(block)
    }
}
```

Users can then set the log level and backend for each plugin independently:

```kotlin
install(MyPlugin) {
    configure {
        logging {
            level(LogLevel.DEBUG)      // default is WARN
            backend(PrintLogger)       // default is PrintLogger
        }
    }
}
```

The `PluginInstallation` system automatically detects `HasPluginConfigure` on the
config object and applies the logging configuration before calling `install()`.

### Shared Services

Plugins can share typed services with each other via the `ServiceRegistry`, replacing
the previous untyped `extras` map. Declare a `ServiceKey<T>` and register/retrieve:

```kotlin
// Declare a service key (typically a top-level val):
val CACHE_SERVICE = ServiceKey<CacheService>("com.example.cache")

// Plugin A registers a service:
override fun install(scope: TransmuteScope, config: MyConfig) {
    scope.services.register(CACHE_SERVICE, InMemoryCache())
}

// Plugin B retrieves it:
override fun install(scope: TransmuteScope, config: MyConfig) {
    val cache = scope.services.getOrNull(CACHE_SERVICE)
    cache?.let { scope.logger.info("Cache integration active") }
}
```

All plugins share the same `ServiceRegistry` instance, so a service registered by
one plugin is immediately available to any plugin installed after it.

### Diagnostics

Plugins can report structured health/status information via `scope.diagnostics`:

```kotlin
override fun install(scope: TransmuteScope, config: MyConfig) {
    scope.diagnostics.report(PluginStatus(
        available = true,
        reason = "GStreamer 1.24.0 resolved from bundled extraction",
        details = mapOf(
            "version" to "1.24.0",
            "gst-launch" to "/usr/bin/gst-launch-1.0",
        ),
    ))
}
```

After building, query diagnostics on the `Transmute` instance using the plugin's
typed `PluginId` — no raw strings needed:

```kotlin
val transmute = Transmute { plugins { install(GStreamer) } }

// Check a specific plugin (using its typed key)
val gstDiag = transmute.diagnostics.plugin(GStreamer.key)
println(gstDiag?.current?.available)  // true

// Summary of all plugins
println(transmute.diagnostics.summary())  // {dev.transmute.gstreamer=true}
```

### Feature Toggles

Plugins declare their supported features as typed `PluginFeature` constants.
Features can be toggled directly on the plugin config or inside a nested
`configure { features { } }` block — both are equivalent:

```kotlin
// Shorthand (recommended):
install(GStreamer) {
    disable(GStreamerFeature.LegacyAvi)       // skip AVI container support
    disable(GStreamerFeature.ImageEncoding)    // skip HEIF/AVIF encoding
}

// Equivalent nested form:
install(GStreamer) {
    configure {
        features {
            disable(GStreamerFeature.LegacyAvi)
            disable(GStreamerFeature.ImageEncoding)
        }
    }
}
```

Plugin code checks feature state at install time using the typed constant:

```kotlin
override fun install(scope: TransmuteScope, config: MyConfig) {
    if (scope.features.isEnabled(GStreamerFeature.ImageEncoding)) {
        scope.codecs.image.encoders.register(HeifEncoder())
    }
}
```

Features default to enabled (`true`) unless the user explicitly disables them.

Plugins expose their feature set for discovery:

```kotlin
object GStreamer : TransmutePlugin<GStreamerPluginConfig> {
    // ...
    override val features = GStreamerFeature.ALL
}

object GStreamerFeature {
    val AudioCodecs   = PluginFeature("audio-codecs", "GStreamer audio codec support")
    val ImageCodecs   = PluginFeature("image-codecs", "GStreamer image codec support")
    val ImageEncoding = PluginFeature("image-encoding", "HEIF/AVIF encoding")
    val VideoCodecs   = PluginFeature("video-codecs", "GStreamer video codec support")
    val LegacyAvi     = PluginFeature("legacy-avi", "Legacy AVI container support")
}
```

### Plugin Ordering & Dependencies

Plugins can declare ordering constraints via three properties on `TransmutePlugin`.
All references use typed `PluginId` instances — never raw strings:

| Property        | Type              | Behavior                                             |
|-----------------|-------------------|------------------------------------------------------|
| `dependsOn`     | `Set<PluginId>`   | **Hard** — dependency must be installed; fails if missing |
| `installAfter`  | `Set<PluginId>`   | **Soft** — install after these plugins if present    |
| `installBefore` | `Set<PluginId>`   | **Soft** — install before these plugins if present   |

```kotlin
object MyPlugin : TransmutePlugin<MyConfig> {
    override val key = pluginId("com.example.my-plugin")
    override val dependsOn = setOf(BasePlugin.key)
    override val installAfter = setOf(CachePlugin.key)

    // ...
}
```

The framework topologically sorts all plugins before installation, detects cycles,
and validates that all hard dependencies are present.

### Lifecycle Hooks

Plugins that need post-install initialization or cleanup can implement `PluginLifecycle`:

```kotlin
object MyPlugin : TransmutePlugin<MyConfig>, PluginLifecycle {
    override val key = pluginId("com.example.my-plugin")

    override fun install(scope: TransmuteScope, config: MyConfig) {
        // Normal installation…
    }

    override fun onInstalled(scope: TransmuteScope) {
        // Called after ALL plugins have been installed.
        // Access services registered by other plugins.
        val cache = scope.services.getOrNull(CACHE_SERVICE)
        cache?.let { scope.logger.info("Cache integration active") }
    }

    override fun onClose() {
        // Called when the Transmute instance is closed.
        // Release native resources, temp files, threads.
    }
}
```

Call `transmute.close()` to trigger `onClose()` on all plugins that implement it.

### Plugin IDs

Every plugin has a unique `PluginId` (reverse-domain style recommended). IDs are
strongly typed via the `PluginId` class, replacing raw strings throughout the
framework's identification, diagnostics, and dependency resolution:

```kotlin
// Declare via the convenience function:
override val key = pluginId("com.example.my-plugin")

// Or construct directly:
override val key = PluginId("com.example.my-plugin")
```

The key's `toString()` returns the plain ID string, so it integrates naturally
with logging, error messages, and diagnostics output.

## GStreamer Plugin

The `GStreamer` plugin object (in `transmute-plugins:gstreamer`) registers GStreamer-backed
codecs for formats not covered by platform-native codecs:

```kotlin
// All features enabled by default
val transmute = Transmute {
    plugins {
        install(GStreamer) {
            // Selectively disable features you don't need:
            // disable(GStreamerFeature.LegacyAvi)
            // disable(GStreamerFeature.ImageEncoding)

            // Enable debug logging for troubleshooting
            configure {
                logging {
                    level(LogLevel.DEBUG)
                }
            }
        }
    }
}
```

GStreamer is **bundled by default** and extracted automatically on first use.
To use a custom installation, call `installFrom(TPath.of("/path/to/gstreamer"))`.
Resolver diagnostics are automatically logged through the plugin logger at
`DEBUG` level — set the log level to `DEBUG` to see detailed resolution info.

### Dependency

Add `transmute-gstreamer` to your dependencies alongside `transmute-api`:

```kotlin
dependencies {
    implementation("dev.transmute:transmute-api:<version>")
    implementation("dev.transmute:transmute-gstreamer:<version>")
}
```

## Backward Compatibility

All existing code using static `Transmute.image { ... }`, `Transmute.codec`, etc.
continues to work. These properties delegate to `Transmute.Default`, a lazy instance
built with platform defaults and no plugins.
