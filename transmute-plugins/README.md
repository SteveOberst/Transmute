# Transmute Plugins

Official plugin catalog for the Transmute media processing library.

## Available Plugins

| Plugin      | Published artifact              | Key                        | Description                                      |
|---|---|---|---|
| `GStreamer` | `transmute-plugins-gstreamer`   | `GStreamer.key`            | OGG/Opus/FLAC, MP4/MOV/WebM/AVI/MKV             |
| `LibHeif`   | `transmute-plugins-libheif`     | `LibHeif.key`              | HEIF/HEIC/AVIF decode/encode via libheif         |

## Installation

Plugins live under `transmute-plugins:<name>` in the Gradle project hierarchy,
but the published artifacts use path-based ids. The same coordinates work from
JitPack and GitHub Packages; only the repository configuration changes.

Add the plugin artifact to your dependencies alongside `transmute-api`:

```kotlin
dependencies {
    implementation("com.github.SteveOberst.Transmute:transmute-api:<version>")
    implementation("com.github.SteveOberst.Transmute:transmute-plugins-gstreamer:<version>")
    implementation("com.github.SteveOberst.Transmute:transmute-plugins-libheif:<version>")
}
```

You normally do not need `transmute-plugins-catalog` directly; the concrete plugin
artifacts bring it in transitively.

For GitHub Packages repository and credential setup, see the root [README](../README.md).

Then install the plugin via the builder DSL:

```kotlin
val transmute = Transmute {
    plugins {
        install(GStreamer)   // audio + video codecs
        install(LibHeif)     // HEIF/HEIC/AVIF image codecs
    }
}
```

### Feature Toggles

Plugins declare typed `PluginFeature` constants for fine-grained control.
Use `configure { features { } }` with strongly-typed feature references:

```kotlin
val transmute = Transmute {
    plugins {
        install(GStreamer) {
            disable(GStreamerFeature.LegacyAvi)       // skip AVI codec
        }
        install(LibHeif) {
            disable(LibHeifFeature.ImageEncoding)      // decode only, no encoding
        }
    }
}
```

**GStreamer features:**

| Feature                          | Default | Description                                           |
|---|---|---|
| `GStreamerFeature.AudioCodecs`   | enabled | AAC, M4A, Opus, FLAC/OGG decode/encode               |
| `GStreamerFeature.VideoCodecs`   | enabled | MP4, MOV, WebM, AVI, MKV                              |
| `GStreamerFeature.LegacyAvi`    | enabled | Legacy AVI container support                           |

**LibHeif features:**

| Feature                          | Default | Description                                           |
|---|---|---|
| `LibHeifFeature.ImageCodecs`    | enabled | HEIF, HEIC, AVIF decode/encode                        |
| `LibHeifFeature.ImageEncoding`  | enabled | HEIF/AVIF encoding via `heif-enc`                     |

## Plugin System Features

Every plugin installed through the `Transmute { }` builder gets access to:

| Feature             | API                                        | Description                              |
|---|---|---|
| Typed keys          | `PluginId`                                 | Strongly-typed plugin identification     |
| Typed features      | `PluginFeature`                            | Strongly-typed feature toggle constants  |
| Typed services      | `scope.services`                           | Type-safe `ServiceRegistry` for sharing  |
| Diagnostics         | `scope.diagnostics`                        | Structured health / status reporting     |
| Feature toggles     | `scope.features` / `configure { features }` | Named enable/disable switches           |
| Per-plugin logging  | `scope.logger`                             | Auto-tagged `PluginLogger`               |
| Ordering            | `dependsOn` / `installAfter` / `installBefore` | Dependency and ordering control     |
| Lifecycle hooks     | `PluginLifecycle`                          | `onInstalled()` + `onClose()` callbacks  |

See [docs/plugins.md](../docs/plugins.md) for the full plugin system documentation.

## Writing a New Plugin

1. Create a new module under `transmute-plugins/` (e.g. `transmute-plugins/my-plugin`).
2. Add the module in `settings.gradle.kts`:
   ```kotlin
   include(":transmute-plugins:my-plugin")
   ```
3. Implement `TransmutePlugin<C>` (or extend `SimpleTransmutePlugin` for config-free plugins).
   Each plugin must declare a `PluginId` and optionally a set of `PluginFeature` constants.
4. Add an entry to the catalog table above.

See the [GStreamer plugin](gstreamer/README.md) for a complete reference implementation.
