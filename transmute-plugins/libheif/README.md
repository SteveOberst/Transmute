# transmute-plugins:libheif

Desktop HEIF/HEIC/AVIF codec plugin for Transmute, backed by the [libheif](https://github.com/strukturag/libheif) CLI tools.

## Overview

Registers `heif-dec` / `heif-enc` subprocess-based codecs for HEIF, HEIC, and AVIF on **Desktop/JVM**. On Android and iOS these formats are supported natively by the platform (Android `BitmapFactory`, iOS `CoreGraphics`/`ImageIO`), so this plugin is a **no-op** on those targets.

If the libheif binaries are not available and cannot be provisioned, the plugin silently skips codec registration and logs a warning.

## Supported Formats

| Format | Decode | Encode |
|--------|--------|--------|
| HEIF (`.heif`) | ✅ | ✅ |
| HEIC (`.heic`) | ✅ | ✅ |
| AVIF (`.avif`) | ✅ | ✅ |

## Installation

```kotlin
val transmute = Transmute {
    plugins {
        install(LibHeif)
    }
}
```

## Feature Toggles

All features are **enabled by default**. Disable selectively:

```kotlin
install(LibHeif) {
    disable(LibHeifFeature.ImageEncoding)  // decode only — skip heif-enc
}
```

| Feature | Default | Description |
|---------|---------|-------------|
| `LibHeifFeature.ImageCodecs` | enabled | Register HEIF/HEIC/AVIF decoders and encoders |
| `LibHeifFeature.ImageEncoding` | enabled | HEIF/HEIC/AVIF encoding via `heif-enc` |

## libheif Binary Resolution

The plugin resolves `heif-dec` / `heif-enc` binaries in this order:

1. **Bundled** (default) — extracts pre-built binaries from the JAR to a temp directory
2. **Custom path** — explicit installation directory via `installFrom()`
3. **System PATH** — searches PATH and platform defaults via `useSystemInstallation()`

### Windows auto-provisioning

On Windows, when bundled resources are absent and no system installation is found, the plugin automatically downloads and caches MSYS2 UCRT64 packages from the MSYS2 CDN:

```
~/.transmute/libheif/<version>/
  bin/
    heif-dec.exe
    heif-enc.exe
    libheif.dll
    ...
```

This makes HEIF/AVIF work **out of the box on Windows** with no manual setup. Requires internet access on the first run only; subsequent runs use the cache.

### Custom installation

```kotlin
install(LibHeif) {
    installFrom(TPath.of("/usr/local/opt/libheif"))
}
```

### System PATH

```kotlin
install(LibHeif) {
    useSystemInstallation()
}
```

## Configuration

```kotlin
install(LibHeif) {
    disable(LibHeifFeature.ImageEncoding)       // decode only
    installFrom(TPath.of("/opt/libheif"))        // custom path
    timeout(60_000L)                             // subprocess timeout ms

    configure {                                  // per-plugin logging
        logging { level(LogLevel.DEBUG) }
    }
}
```

## Key Types

| Type | Purpose |
|------|---------|
| `LibHeif` | `TransmutePlugin<LibHeifPluginConfig>` — the plugin object |
| `LibHeif.key` | `PluginId` — strongly-typed plugin identifier |
| `LibHeifFeature` | Typed feature toggle constants |
| `LibHeifPluginConfig` | Configuration: installation mode, feature toggles, timeout |
| `LibHeifInstallation` | Sealed class: `Bundled`, `System`, `Custom(home)` |
| `LibHeifCodecInstaller` | Installs codec registrations into image registries |

### Runtime availability check

```kotlin
if (LibHeifCodecInstaller.available) {
    // heif-dec and heif-enc are locatable on this system
}
```

### Diagnostics

```kotlin
val diag = transmute.diagnostics.plugin(LibHeif.key)
println(diag?.current?.available)
```

## Platform Notes

| Platform | Status |
|----------|--------|
| Desktop (JVM) | ✅ Subprocess via `heif-dec` / `heif-enc`; auto-provisions on Windows |
| Android | No-op (platform HEIF support is built-in via `BitmapFactory`) |
| iOS | No-op (platform HEIF support is built-in via `CoreGraphics`) |

## Dependencies

- `transmute-api`
- `transmute-image`

## Targets

Desktop JVM only (Android and iOS source sets are compiled as no-ops).
