# Transmute

Kotlin Multiplatform media conversion, compression, and transformation — image, audio, and video — with a single API across Android, Desktop/JVM, and iOS.

[![JitPack](https://jitpack.io/v/SteveOberst/Transmute.svg)](https://jitpack.io/#SteveOberst/Transmute)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.21-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

## Features

- Single `commonMain` API for image, audio, and video across Android, Desktop (JVM), and iOS
- Instance-based API with plugin system — create isolated `Transmute` instances with custom codec configurations
- Platform-native codecs by default — no external dependencies for common formats
- Optional `transmute-plugins:gstreamer` fills platform gaps (HEIF/AVIF on Desktop, Opus/OGG on iOS, video on Desktop, etc.)
- Optional `transmute-plugins:libheif` for bundled HEIF/AVIF support on Desktop without a system GStreamer install
- Pure-Kotlin WAV and BMP codecs that work on all platforms without native dependencies
- Pipeline-based decode → transform → encode: swap or extend individual stages without touching the rest
- 27 transforms across all three domains (scale, crop, rotate, blur, normalize, trim, fade, gain, speed, compressor, etc.)
- Structure reading: parse files into typed Kotlin data classes mirroring the on-disk format (PNG chunks, JPEG segments, RIFF containers, ISO-BMFF boxes, etc.) without decoding pixel/sample data
- Metadata reading: extract EXIF, XMP, ICC, ID3v1/v2, Vorbis, RIFF INFO, iTunes, Matroska tags without full decode
- Suspending I/O via `TSource` and `TSink` — non-blocking byte streams on every platform
- Configurable logging with level filtering and pluggable backends

## Supported Formats

| Domain | Formats |
|--------|---------|
| Image  | JPEG, PNG, WebP, HEIF, HEIC, AVIF, GIF, BMP, TIFF |
| Audio  | MP3, AAC, WAV, OGG, FLAC, M4A, Opus |
| Video  | MP4, WebM, MOV, AVI, MKV |

## Setup

Transmute is published via [JitPack](https://jitpack.io/#SteveOberst/Transmute).

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io")
    }
}
```

```kotlin
// build.gradle.kts
dependencies {
    // Core API (required)
    implementation("com.github.SteveOberst.Transmute:transmute-api:<version>")

    // Optional: GStreamer plugin for advanced codec support
    implementation("com.github.SteveOberst.Transmute:transmute-plugins-gstreamer:<version>")

    // Optional: libheif plugin for bundled HEIF/AVIF on Desktop
    implementation("com.github.SteveOberst.Transmute:transmute-plugins-libheif:<version>")
}
```

## Quick Start

```kotlin
// --- Image ---

// Scale to fit within bounds, convert to JPEG
val jpegBytes: ByteArray = Transmute.image {
    scale(maxWidth = 1920, maxHeight = 1080)
    encode { options { outputFormat = OutputFormat.Exact(ImageFormat.Jpeg) } }
}.transmute(pngBytes.asBytes()).bytes.data

// Resize to exact dimensions with Lanczos resampling, fixed output type
val resized = Transmute.image.to(ImageFormat.Png) {
    resize(800, 600, filter = ResampleFilter.LANCZOS3)
}.transmute(pngBytes.asBytes())  // returns EncodedBytes<ImageFormat.Png>

// --- Audio ---

// Normalize, trim, and fade — preserves input format if encodable, else falls back to WAV
val audioOut: ByteArray = Transmute.audio {
    normalize(targetPeak = 0.9f)
    trim(startMs = 1_000, endMs = 5_000)
    fade(fadeInMs = 100, fadeOutMs = 200)
}.transmute(wavBytes.asBytes()).bytes.data

// --- Video ---

// Resize frames, trim duration, force MP4 output
val videoOut: ByteArray = Transmute.video {
    resize(maxWidth = 1280, maxHeight = 720)
    trim(startMs = 0, endMs = 30_000)
    encode { options { outputFormat = OutputFormat.Exact(VideoFormat.Mp4) } }
}.transmute(mp4Bytes.asBytes()).bytes.data
```

## Inspect & Format Detection

```kotlin
// Detect format from raw bytes (image + audio + video)
val format = Transmute.inspect.detectFormat(bytes)

// Decode metadata without a full transcode
val metadata: List<MediaMetadata> = Transmute.inspect.metadata(bytes)
for (meta in metadata) {
    when (meta) {
        is ExifMetadata -> println("EXIF: ${meta.tags}")
        is XmpMetadata  -> println("XMP packet present")
        is Id3v2Metadata -> println("ID3 title: ${meta.title}")
    }
}

// One-call inspection: detect format + structure + metadata in a single pass
val inspection: MediaInspection = Transmute.inspect.inspect(bytes)
println("Format: ${inspection.format.label}, size: ${inspection.sizeBytes} bytes")
println("Structure: ${inspection.structure}")

// Parse file structure without decoding pixel/sample data
val structure = Transmute.inspect.structure(pngBytes.asBytes(), ImageFormat.Png)

// Extract thumbnail from first video frame
val thumbnail: EncodedBytes<ImageFormat> =
    Transmute.inspect.video.thumbnailFirstFrame(videoSource)
```

## Plugin System

```kotlin
// Create an isolated Transmute instance with the GStreamer plugin
val transmute = Transmute {
    plugins {
        install(GStreamerPlugin) {
            domains(MediaDomain.VIDEO or MediaDomain.AUDIO)
        }
    }
}

val mp4Out = transmute.video {
    resize(1280, 720)
}.transmute(source)
```

See [docs/plugins.md](docs/plugins.md) for the full plugin API.

## Logging

```kotlin
// Global — warnings and errors go to stdout by default
TransmuteLogging.configure(LogLevel.INFO)

// Per-operation override
Transmute.image {
    logger(TransmuteLogging.printLogger(LogLevel.DEBUG))
    scale(800, 600)
}.transmute(bytes)
```

See [docs/logging.md](docs/logging.md) for custom logger backends.

## Documentation

| Topic | File |
|-------|------|
| Conversion examples | [docs/examples.md](docs/examples.md) |
| Format detection | [docs/format-detection.md](docs/format-detection.md) |
| Inspect API | [docs/inspect.md](docs/inspect.md) |
| Structure reading | [docs/structures.md](docs/structures.md) |
| Pipeline customisation | [docs/pipelines.md](docs/pipelines.md) |
| One-shot codec access | [docs/codec.md](docs/codec.md) |
| Plugin system | [docs/plugins.md](docs/plugins.md) |
| Extending Transmute | [docs/extending.md](docs/extending.md) |
| Logging | [docs/logging.md](docs/logging.md) |
| All transforms | [docs/transforms/README.md](docs/transforms/README.md) |
| All formats | [docs/codecs/README.md](docs/codecs/README.md) |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).
