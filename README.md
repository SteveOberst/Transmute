# Transmute

Kotlin Multiplatform media conversion — image, audio, and video — with a single API across Android, Desktop/JVM, and iOS.

[![JitPack](https://jitpack.io/v/SteveOberst/Transmute.svg)](https://jitpack.io/#SteveOberst/Transmute)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.21-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

## Why Transmute?

- **One API, three platforms** — write conversion code in `commonMain`, run it on Android (MediaCodec), Desktop (ImageIO + FFmpeg), and iOS (CoreGraphics + AVFoundation).
- **Batteries included** — FFmpeg ships bundled for desktop. No external installs needed.
- **Pure-Kotlin codecs** — WAV and BMP work everywhere, even without platform APIs.
- **Kernel-based image resize** — six resampling filters (Nearest, Bilinear, Mitchell, Catmull-Rom, Lanczos3) with proper anti-aliasing for downscale.
- **26+ transforms** — scale, crop, rotate, blur, normalize, trim, fade, gain, speed, compressor, and more across all three media types.

## Quick Start

```kotlin
// Scale and convert to JPEG
val jpeg = Transmute.image(pngBytes) {
    scale(maxWidth = 1920, maxHeight = 1080)
    quality(0.85f)
    outputFormat(ImageFormat.JPEG)
}

// Resize to exact dimensions with Lanczos resampling
val resized = Transmute.image(sourceBytes) {
    resize(800, 600, filter = ResampleFilter.LANCZOS3)
}

// Normalize and trim audio
val audio = Transmute.audio(wavBytes) {
    normalize(targetPeak = 0.9f)
    trim(startMs = 1000, endMs = 5000)
    fade(fadeInMs = 100, fadeOutMs = 200)
}

// Resize video
val video = Transmute.video(mp4Bytes) {
    resize(maxWidth = 1280, maxHeight = 720)
    trim(startMs = 0, endMs = 30_000)
}

// Detect format from raw bytes
val format = Transmute.detectFormat(bytes)
```

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
            implementation("com.github.SteveOberst.Transmute:transmute-api:0.1.1")
        }
    }
}

// or Android/JVM only
dependencies {
    implementation("com.github.SteveOberst.Transmute:transmute-api:0.1.1")
}
```

Individual modules are also available: `transmute-core`, `transmute-image`, `transmute-audio`, `transmute-video`.

## Modules

| Module | Purpose |
|--------|---------|
| `transmute-api` | Public facade — `Transmute` object, `Transformers` factory, DSL extension functions |
| `transmute-core` | Codec/Transform base types, pipeline, format enums, `TransmuteConfig` |
| `transmute-image` | Image codecs (JPEG, PNG, WebP, HEIF, AVIF, GIF, BMP, TIFF) + transforms |
| `transmute-audio` | Audio codecs (WAV, MP3, AAC, FLAC, OGG, OPUS, M4A) + transforms |
| `transmute-video` | Video codecs (MP4, MOV, WebM, AVI, MKV) + transforms |

## Codec Support

### Image

| Format | Android | Desktop | iOS |
|--------|---------|---------|-----|
| JPEG | decode + encode | decode + encode | decode + encode |
| PNG | decode + encode | decode + encode | decode + encode |
| WebP | decode + encode | decode + encode | decode + encode |
| HEIF/HEIC | decode | decode + encode ¹ | decode + encode |
| AVIF | decode | decode + encode ¹ | decode + encode ² |
| GIF | decode | decode + encode | decode + encode |
| BMP | decode + encode ³ | decode + encode ³ | decode + encode |
| TIFF | decode | decode + encode | decode + encode |

### Audio

| Format | Android | Desktop | iOS |
|--------|---------|---------|-----|
| WAV | decode + encode ³ | decode + encode ³ | decode + encode ³ |
| MP3 | decode + encode | decode + encode | decode |
| AAC | decode + encode | decode + encode ¹ | decode + encode |
| M4A | decode + encode | decode + encode ¹ | decode + encode |
| FLAC | decode + encode | decode + encode ¹ | decode + encode |
| OGG | decode | decode + encode ¹ | — |
| OPUS | decode + encode ⁴ | decode + encode ¹ | — |

### Video

| Format | Android | Desktop | iOS |
|--------|---------|---------|-----|
| MP4 | decode + encode | decode + encode ¹ | decode + encode |
| MOV | decode + encode | decode + encode ¹ | decode + encode |
| WebM | decode | decode + encode ¹ | — |
| AVI | — | decode + encode ¹ | — |
| MKV | — | decode + encode ¹ | — |

> ¹ Uses bundled FFmpeg (no setup needed). ² iOS 16+ required. ³ Pure Kotlin — works on all platforms. ⁴ Encode requires Android API 29+.

**Engines:** Android uses `BitmapFactory`/`MediaCodec`, Desktop uses `ImageIO` + `TwelveMonkeys` + `FFmpeg`, iOS uses `CoreGraphics` + `AVFoundation`.

## Transforms

### Image
`scale` · `resize` · `crop` · `rotate` · `grayscale` · `flip` · `brightnessContrast` · `blur` · `opacity`

### Audio
`normalize` · `resample` · `fade` · `trim` · `gain` · `mono` · `reverse` · `speed` · `silenceTrim` · `compressor` · `channelMap`

### Video
`trim` · `resize` · `frameRate` · `removeAudio` · `crop` · `speed` · `rotate`

All transforms are platform-independent and operate on intermediate representations.

## FFmpeg Configuration

Desktop codecs use a **bundled** FFmpeg by default — zero configuration needed.

```kotlin
// Use system FFmpeg instead
TransmuteConfig.ffmpeg = FfmpegConfig.System("/usr/local/bin/ffmpeg", "/usr/local/bin/ffprobe")

// Disable FFmpeg (FFmpeg-dependent codecs won't register)
TransmuteConfig.ffmpeg = FfmpegConfig.Disabled
```

The bundled binary is extracted to `~/.transmute/ffmpeg/` on first use. Supported: Windows x64, Linux x64, macOS x64/ARM64.

## Custom Codecs & Transforms

```kotlin
// Register a custom codec
class MyCodec : ImageCodec {
    override val decodableFormats = setOf(ImageFormat.WEBP)
    override val encodableFormats = setOf(ImageFormat.WEBP)
    override fun sniff(data: ByteArray): ImageFormat? { /* magic bytes */ }
    override suspend fun decode(source: ByteArray, context: ConversionContext): ImageIR { /* ... */ }
    override suspend fun encode(ir: ImageIR, context: ConversionContext): ByteArray { /* ... */ }
}
ImageRegistries.register(MyCodec())

// Custom transform — no registration needed
class WatermarkTransform(private val logo: ByteArray) : Transform<ImageIR> {
    override val id = TransformId("image.watermark")
    override suspend fun apply(ir: ImageIR, ctx: ConversionContext): ImageIR { /* ... */ }
}

Transmute.image(photo) {
    transform { add(WatermarkTransform(logoPng)) }
}
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for setup instructions, coding conventions,
and how to add codecs or transforms.

Uses [Conventional Commits](https://www.conventionalcommits.org/) and [release-please](https://github.com/googleapis/release-please) for automated versioning.

## License

MIT License
