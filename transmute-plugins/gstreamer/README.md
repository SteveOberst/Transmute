# transmute-gstreamer

Optional GStreamer integration plugin for Transmute.

## Overview

Registers GStreamer-backed codecs for formats that lack pure-Kotlin/JVM
implementations. Uses subprocess on Desktop/JVM, JNI on Android, and cinterop on
iOS.

## Supported Formats

| Domain | Formats                                     |
|---|---|
| Audio  | AAC, M4A, Opus, FLAC (encode), OGG (encode) |
| Video  | MP4, MOV, WebM, AVI, MKV                    |

## Installation

```kotlin
// All features (audio, video) are enabled by default
val transmute = Transmute {
    plugins {
        install(GStreamer)
    }
}
```

## Feature Toggles

GStreamer declares typed `PluginFeature` constants for fine-grained codec control.
All features are **enabled by default** - disable the ones you don't need:

```kotlin
install(GStreamer) {
    disable(GStreamerFeature.LegacyAvi)       // skip AVI container
}
```

| Feature                          | Default | Description                                           |
|---|---|---|
| `GStreamerFeature.AudioCodecs`   | enabled | AAC, M4A, Opus, FLAC/OGG encode                      |
| `GStreamerFeature.VideoCodecs`   | enabled | MP4, MOV, WebM, AVI, MKV                              |
| `GStreamerFeature.LegacyAvi`    | enabled | Legacy AVI container support                           |

## Key Types

| Type                      | Purpose                                                            |
|---|---|
| `GStreamer`               | `TransmutePlugin<GStreamerPluginConfig>` - the plugin object       |
| `GStreamer.key`           | `PluginId` - strongly-typed plugin identifier                      |
| `GStreamerFeature`        | Typed feature constants for codec control                          |
| `GStreamerPluginConfig`   | Configuration: feature toggles, installation mode, timeout, logging |
| `GStreamerCodecInstaller` | Installs GStreamer codecs into registries                           |

### Runtime Detection

```kotlin
if (GStreamerCodecInstaller.available) {
    // GStreamer is installed on this system
}
```

### Diagnostics

Query plugin health using the typed key:

```kotlin
val transmute = Transmute { plugins { install(GStreamer) } }
val diag = transmute.diagnostics.plugin(GStreamer.key)
println(diag?.current?.available)  // true
```

## Requirements

- **Desktop:** GStreamer SDK installed and `gst-launch-1.0` / `gst-inspect-1.0` on PATH
- **Android:** GStreamer Android SDK (NDK)
- **iOS:** GStreamer iOS framework

## Dependencies

- `transmute-api`
- `transmute-audio`, `transmute-image`, `transmute-video`
- `kotlinx-coroutines-core`

## Targets

Android, Desktop JVM, iOS - via Kotlin Multiplatform.
