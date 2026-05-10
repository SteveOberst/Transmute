# transmute-plugins-libheif

Desktop HEIF/HEIC/AVIF codec plugin for Transmute, backed by the [libheif](https://github.com/strukturag/libheif) CLI tools.

Published artifact: `com.github.SteveOberst.Transmute:transmute-plugins-libheif:<version>`
Source module: `:transmute-plugins:libheif`

## Overview

Registers `heif-dec` / `heif-enc` subprocess-based codecs for HEIF, HEIC, and AVIF on **Desktop/JVM**. On Android and iOS these formats are supported natively by the platform (Android `BitmapFactory`, iOS `CoreGraphics`/`ImageIO`), so this plugin is a **no-op** on those targets.

If the libheif binaries are not available and cannot be provisioned, the plugin silently skips codec registration and logs a warning.

## Supported Formats

| Format | Decode | Encode |
|---|---|---|
| HEIF (`.heif`) | yes | yes |
| HEIC (`.heic`) | yes | yes |
| AVIF (`.avif`) | yes | yes |

## Licensing

libheif itself is **LGPL-3.0**.  However, the codec plugins it bundles carry their own licenses, and **HEIC encoding uses x265 which is GPL-2.0 (or commercial)**.

| Codec    | Format      | Operation | License              |
|---|---|---|---|
| libde265 | HEIF / HEIC | Decode    | LGPL-3.0             |
| x265     | HEIF / HEIC | Encode    | **GPL-2.0** / commercial |
| libaom   | AVIF        | Both      | BSD-2-Clause         |
| libdav1d | AVIF        | Decode    | BSD-2-Clause         |
| rav1e    | AVIF        | Encode    | BSD-2-Clause         |

### x265 and the GPL

x265 is dual-licensed: **GPL-2.0** (open source) or a paid commercial license.

Distributing software that includes x265 (directly or via libheif) requires either:
- Full source disclosure under GPL-2.0-compatible terms, **or**
- A paid commercial x265 license: https://www.x265.org/license-comparison/

This applies to most uses of HEIC/HEIF *encoding*.  AVIF encoding (aom / rav1e) and all *decoding* use permissive-licensed codecs and are **not** affected.

**If you want to avoid GPL entirely**, disable `LibHeifFeature.ImageEncoding`.  HEIC/HEIF *decoding* and full AVIF support remain available under LGPL/BSD.

Reference: https://www.videolan.org/developers/x265.html

## Installation

Add the plugin dependency, then install it through the Transmute builder:

```kotlin
dependencies {
    implementation("com.github.SteveOberst.Transmute:transmute-api:<version>")
    implementation("com.github.SteveOberst.Transmute:transmute-plugins-libheif:<version>")
}
```

```kotlin
val transmute = transmute {
    plugins {
        install(LibHeif)
    }
}
```

## Feature Toggles

All features are **enabled by default**. Disable selectively:

```kotlin
install(LibHeif) {
    disable(LibHeifFeature.ImageEncoding)  // decode only -- avoids x265 / GPL
}
```

| Feature | Default | Description |
|---|---|---|
| `LibHeifFeature.ImageCodecs` | enabled | Register HEIF/HEIC/AVIF decoders and encoders |
| `LibHeifFeature.ImageEncoding` | enabled | HEIF/HEIC/AVIF encoding via `heif-enc` (uses x265 -- see GPL note above) |

## libheif Binary Resolution

The plugin resolves `heif-dec` / `heif-enc` binaries in this order:

1. **Bundled** (default) -- extracts pre-built binaries from the JAR to a temp directory
2. **Custom path** -- explicit installation directory via `installFrom()`
3. **System PATH** -- searches PATH and platform defaults via `useSystemInstallation()`
4. **vcpkg auto-provision** (Windows only) -- locates vcpkg, installs libheif if needed, and caches the result under `~/.transmute/libheif/`

### Windows (vcpkg)

On Windows, libheif is installed and staged via [vcpkg](https://learn.microsoft.com/en-us/vcpkg/get_started/get-started).

**Prerequisites (build time):** vcpkg must be installed with `VCPKG_ROOT` set, or with `vcpkg` on `PATH`.

Quick install (PowerShell):
```powershell
git clone https://github.com/microsoft/vcpkg $env:USERPROFILE\vcpkg
& "$env:USERPROFILE\vcpkg\bootstrap-vcpkg.bat"
$env:VCPKG_ROOT = "$env:USERPROFILE\vcpkg"
```

Then stage the binaries before building a distribution JAR:
```
./gradlew :transmute-plugins:libheif:stageLibHeifDesktop
```

The staging task runs:
```
vcpkg install "libheif[tools,aom,dav1d,rav1e,hevc,x265]" --triplet x64-windows
```

To override the feature set (e.g. to drop x265 / GPL), add to `gradle.properties`:
```properties
transmute.libheif.vcpkgFeatures=tools,aom,dav1d,rav1e,hevc
```

**Runtime auto-provision:** when bundled binaries are absent, the plugin also attempts to locate vcpkg at runtime and installs libheif automatically. This is primarily intended for developers running directly from source.

### macOS (Homebrew)

```
brew install libheif
./gradlew :transmute-plugins:libheif:stageLibHeifDesktop
```

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
    disable(LibHeifFeature.ImageEncoding)       // decode only (avoids GPL)
    installFrom(TPath.of("/opt/libheif"))        // custom path
    timeout(60_000L)                             // subprocess timeout ms

    configure {                                  // per-plugin logging
        logging { level(LogLevel.DEBUG) }
    }
}
```

## Key Types

| Type | Purpose |
|---|---|
| `LibHeif` | `TransmutePlugin<LibHeifPluginConfig>` -- the plugin object |
| `LibHeif.key` | `PluginId` -- strongly-typed plugin identifier |
| `LibHeifFeature` | Typed feature toggle constants (with full licensing notes) |
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
|---|---|
| Desktop (JVM) | Subprocess via `heif-dec` / `heif-enc`; staged via vcpkg (Windows) or Homebrew (macOS) |
| Android | No-op (platform HEIF support is built-in via `BitmapFactory`) |
| iOS | No-op (platform HEIF support is built-in via `CoreGraphics`) |

## Dependencies

- `transmute-api`
- `transmute-image`