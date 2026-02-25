# Transmute

Kotlin Multiplatform library for cross-platform media transcoding and transformation (image/audio/video) using a format-agnostic intermediate representation and typed decode/transform/encode pipelines.

[![JitPack](https://jitpack.io/v/SteveOberst/Transmute.svg)](https://jitpack.io/#SteveOberst/Transmute)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.21-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

## Features

- Single `commonMain` API across Android, Desktop/JVM, and iOS
- Decode -> IR transforms -> encode as typed handler chains (swap defaults or build custom pipelines)
- One-shot decode/encode facade via `Transmute.codec()`
- Format detection + lightweight inspection via `Transmute.inspect()`
- Bundled FFmpeg on Desktop (no external install required); pure Kotlin BMP/WAV fallbacks
- Structured logging (default level: `WARN`)

## Quick Start

```kotlin
suspend fun quickStart(
  pngBytes: ByteArray,
  wavBytes: ByteArray,
  mp4Bytes: ByteArray,
) {
  // Transmute uses `Bytes` as the canonical binary type.
  // Use `ByteArray.asBytes()` when your inputs are raw byte arrays.

  val jpegBytes =
    Transmute.image {
      scale(maxWidth = 1920, maxHeight = 1080)
      encode { options(dev.transmute.image.JpegEncodeOptions(quality = 0.85f)) }
    }.transmute(pngBytes.asBytes()).bytes.data

  val aacBytes =
    Transmute.audio {
      trim(startMs = 1_000, endMs = 5_000)
      encode { options { outputFormat = dev.transmute.core.OutputFormat.Exact(dev.transmute.audio.AudioFormat.Aac) } }
    }.transmute(wavBytes.asBytes()).bytes.data

  val mp4Preview =
    Transmute.video {
      trim(startMs = 0, endMs = 15_000)
      removeAudio()
      encode { options { outputFormat = dev.transmute.core.OutputFormat.Exact(dev.transmute.video.VideoFormat.Mp4) } }
    }.transmute(mp4Bytes.asBytes()).bytes.data

  val format = Transmute.inspect().detectFormat(pngBytes.asBytes())
}
```

## Documentation

Start here: `docs/README.md`

- `docs/pipelines.md` (custom decode/encode pipelines, platform-native inputs/outputs)
- `docs/codec.md` (`Transmute.codec()` decode/encode, range decode)
- `docs/inspect.md` (`Transmute.inspect()` format detection and inspection)
- `docs/examples.md` (end-to-end transmuter recipes)
- `docs/codecs/README.md` (codec matrix and per-format notes)
- `docs/transforms/README.md` (transform catalog and per-transform docs)
- `docs/logging.md`, `docs/ffmpeg.md`, `docs/extending.md`

## Installation

Add JitPack and the dependency:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
  repositories {
    google()
    mavenCentral()
    maven("https://jitpack.io")
  }
}

// build.gradle.kts (KMP)
kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation("com.github.SteveOberst.Transmute:transmute-api:<version>")
    }
  }
}

// or Android/JVM only
dependencies {
  implementation("com.github.SteveOberst.Transmute:transmute-api:<version>")
}
```

Individual modules are also available: `transmute-core`, `transmute-image`, `transmute-audio`, `transmute-video`.

## Modules

| Module | Purpose |
|---|---|
| `transmute-api` | Public facade (`Transmute`, DSL, codec + inspect) |
| `transmute-core` | Base types (`Bytes`, formats, pipelines, logging, `TransmuteConfig`) |
| `transmute-image` | Image codecs + transforms |
| `transmute-audio` | Audio codecs + transforms |
| `transmute-video` | Video codecs + transforms |

## Contributing

See `CONTRIBUTING.md`.

## License

MIT. See `LICENSE`.
