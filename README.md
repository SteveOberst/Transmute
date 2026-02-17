# Transmute

Kotlin Multiplatform media conversion, compression and transformation - image, audio, and video - with a single API across Android, Desktop/JVM, and iOS.

[![JitPack](https://jitpack.io/v/SteveOberst/Transmute.svg)](https://jitpack.io/#SteveOberst/Transmute)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.21-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

## Features

- Single `commonMain` API for image, audio, and video conversion across Android, Desktop (JVM), and iOS
- Bundled FFmpeg for desktop - no external install required
- Pure-Kotlin WAV and BMP codecs that work on all platforms without native dependencies
- Image resample filters: Nearest, Bilinear, Mitchell, Catmull-Rom, Lanczos3 (with anti-alias for downscale)
- 27 transforms across all three media types (scale, crop, rotate, blur, normalize, trim, fade, gain, speed, compressor, etc.)
- Configurable logging with level filtering and pluggable logger backends

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

## Logging

Transmute uses a structured logging API. By default, logging is set to `INFO` level.

```kotlin
import dev.transmute.core.TransmuteLogging
import dev.transmute.core.LogLevel

// Silence all logging
TransmuteLogging.configure(LogLevel.OFF)

// Only warnings and errors
TransmuteLogging.configure(LogLevel.WARN)

// Debug-level (verbose)
TransmuteLogging.configure(LogLevel.DEBUG)

// Supply a custom logger backend
TransmuteLogging.configure(LogLevel.INFO, myConversionLogger)

// Per-operation logger override
val result = Transmute.image(bytes) {
    logger(myLogger)
    scale(maxWidth = 800, maxHeight = 600)
}
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

| Module            | Purpose                                                                             |
|-------------------|-------------------------------------------------------------------------------------|
| `transmute-api`   | Public facade - `Transmute` object, `Transformers` factory, DSL extension functions |
| `transmute-core`  | Codec/Transform base types, pipeline, format enums, logging, `TransmuteConfig`      |
| `transmute-image` | Image codecs (JPEG, PNG, WebP, HEIF, AVIF, GIF, BMP, TIFF) + transforms             |
| `transmute-audio` | Audio codecs (WAV, MP3, AAC, FLAC, OGG, OPUS, M4A) + transforms                     |
| `transmute-video` | Video codecs (MP4, MOV, WebM, AVI, MKV) + transforms                                |

## Codec Support

### Image

| Format                           | Android           | Desktop           | iOS               | Docs                           |
|----------------------------------|-------------------|-------------------|-------------------|--------------------------------|
| [JPEG](docs/codecs/jpeg.md)      | decode + encode   | decode + encode   | decode + encode   | [jpeg.md](docs/codecs/jpeg.md) |
| [PNG](docs/codecs/png.md)        | decode + encode   | decode + encode   | decode + encode   | [png.md](docs/codecs/png.md)   |
| [WebP](docs/codecs/webp.md)      | decode + encode   | decode + encode   | decode + encode   | [webp.md](docs/codecs/webp.md) |
| [HEIF/HEIC](docs/codecs/heif.md) | decode            | decode + encode ¹ | decode + encode   | [heif.md](docs/codecs/heif.md) |
| [AVIF](docs/codecs/avif.md)      | decode            | decode + encode ¹ | decode + encode ² | [avif.md](docs/codecs/avif.md) |
| [GIF](docs/codecs/gif.md)        | decode            | decode + encode   | decode + encode   | [gif.md](docs/codecs/gif.md)   |
| [BMP](docs/codecs/bmp.md)        | decode + encode ³ | decode + encode ³ | decode + encode   | [bmp.md](docs/codecs/bmp.md)   |
| [TIFF](docs/codecs/tiff.md)      | decode            | decode + encode   | decode + encode   | [tiff.md](docs/codecs/tiff.md) |

### Audio

| Format                      | Android           | Desktop           | iOS               | Docs                           |
|-----------------------------|-------------------|-------------------|-------------------|--------------------------------|
| [WAV](docs/codecs/wav.md)   | decode + encode ³ | decode + encode ³ | decode + encode ³ | [wav.md](docs/codecs/wav.md)   |
| [MP3](docs/codecs/mp3.md)   | decode + encode   | decode + encode   | decode            | [mp3.md](docs/codecs/mp3.md)   |
| [AAC](docs/codecs/aac.md)   | decode + encode   | decode + encode ¹ | decode + encode   | [aac.md](docs/codecs/aac.md)   |
| [M4A](docs/codecs/m4a.md)   | decode + encode   | decode + encode ¹ | decode + encode   | [m4a.md](docs/codecs/m4a.md)   |
| [FLAC](docs/codecs/flac.md) | decode + encode   | decode + encode ¹ | decode + encode   | [flac.md](docs/codecs/flac.md) |
| [OGG](docs/codecs/ogg.md)   | decode            | decode + encode ¹ | -                 | [ogg.md](docs/codecs/ogg.md)   |
| [OPUS](docs/codecs/opus.md) | decode + encode ⁴ | decode + encode ¹ | -                 | [opus.md](docs/codecs/opus.md) |

### Video

| Format                      | Android         | Desktop           | iOS             | Docs                           |
|-----------------------------|-----------------|-------------------|-----------------|--------------------------------|
| [MP4](docs/codecs/mp4.md)   | decode + encode | decode + encode ¹ | decode + encode | [mp4.md](docs/codecs/mp4.md)   |
| [MOV](docs/codecs/mov.md)   | decode + encode | decode + encode ¹ | decode + encode | [mov.md](docs/codecs/mov.md)   |
| [WebM](docs/codecs/webm.md) | decode          | decode + encode ¹ | -               | [webm.md](docs/codecs/webm.md) |
| [AVI](docs/codecs/avi.md)   | -               | decode + encode ¹ | -               | [avi.md](docs/codecs/avi.md)   |
| [MKV](docs/codecs/mkv.md)   | -               | decode + encode ¹ | -               | [mkv.md](docs/codecs/mkv.md)   |

> ¹ Uses bundled FFmpeg (no setup needed). ² iOS 16+ required. ³ Pure Kotlin - works on all platforms. ⁴ Encode requires Android API 29+.

**Engines:** Android uses `BitmapFactory`/`MediaCodec`, Desktop uses `ImageIO` + `TwelveMonkeys` + `FFmpeg`, iOS uses `CoreGraphics` + `AVFoundation`.

## Transforms

All transforms are platform-independent and operate on intermediate representations.

### Image

| Class                              | DSL                  | Description                                                  | Docs                                                                   |
|------------------------------------|----------------------|--------------------------------------------------------------|------------------------------------------------------------------------|
| `ImageScaleTransform`              | `scale`              | Fit within bounds, preserve aspect ratio                     | [scale.md](docs/transforms/image/scale.md)                             |
| `ImageResizeTransform`             | `resize`             | Exact resize with resample filter (Lanczos3, Mitchell, etc.) | [resize.md](docs/transforms/image/resize.md)                           |
| `ImageCropTransform`               | `crop`               | Crop to sub-region                                           | [crop.md](docs/transforms/image/crop.md)                               |
| `ImageRotateTransform`             | `rotate`             | Auto-rotate from EXIF orientation                            | [rotate.md](docs/transforms/image/rotate.md)                           |
| `ImageGrayscaleTransform`          | `grayscale`          | BT.709 luma conversion                                       | [grayscale.md](docs/transforms/image/grayscale.md)                     |
| `ImageFlipTransform`               | `flip`               | Mirror horizontally / vertically                             | [flip.md](docs/transforms/image/flip.md)                               |
| `ImageBrightnessContrastTransform` | `brightnessContrast` | Adjust brightness (−255..+255) and contrast (0..3)           | [brightness-contrast.md](docs/transforms/image/brightness-contrast.md) |
| `ImageBlurTransform`               | `blur`               | Box blur with configurable radius                            | [blur.md](docs/transforms/image/blur.md)                               |
| `ImageOpacityTransform`            | `opacity`            | Adjust alpha channel                                         | [opacity.md](docs/transforms/image/opacity.md)                         |

### Audio

| Class                       | DSL           | Description                                         | Docs                                                     |
|-----------------------------|---------------|-----------------------------------------------------|----------------------------------------------------------|
| `AudioNormalizeTransform`   | `normalize`   | Peak amplitude normalization                        | [normalize.md](docs/transforms/audio/normalize.md)       |
| `AudioResampleTransform`    | `resample`    | Resample to target sample rate                      | [resample.md](docs/transforms/audio/resample.md)         |
| `AudioFadeTransform`        | `fade`        | Fade-in / fade-out envelopes                        | [fade.md](docs/transforms/audio/fade.md)                 |
| `AudioTrimTransform`        | `trim`        | Trim to time range                                  | [trim.md](docs/transforms/audio/trim.md)                 |
| `AudioGainTransform`        | `gain`        | Volume gain in dB                                   | [gain.md](docs/transforms/audio/gain.md)                 |
| `AudioMonoTransform`        | `mono`        | Stereo → mono                                       | [mono.md](docs/transforms/audio/mono.md)                 |
| `AudioReverseTransform`     | `reverse`     | Reverse playback                                    | [reverse.md](docs/transforms/audio/reverse.md)           |
| `AudioSpeedTransform`       | `speed`       | Playback speed (SOLA time-stretch, no pitch change) | [speed.md](docs/transforms/audio/speed.md)               |
| `AudioSilenceTrimTransform` | `silenceTrim` | Trim silence from start / end                       | [silence-trim.md](docs/transforms/audio/silence-trim.md) |
| `AudioCompressorTransform`  | `compressor`  | Dynamic range compressor                            | [compressor.md](docs/transforms/audio/compressor.md)     |
| `AudioChannelMapTransform`  | `channelMap`  | Remap audio channels                                | [channel-map.md](docs/transforms/audio/channel-map.md)   |

### Video

| Class                       | DSL           | Description                              | Docs                                                     |
|-----------------------------|---------------|------------------------------------------|----------------------------------------------------------|
| `VideoTrimTransform`        | `trim`        | Trim to time range                       | [trim.md](docs/transforms/video/trim.md)                 |
| `VideoResizeTransform`      | `resize`      | Fit within bounds, preserve aspect ratio | [resize.md](docs/transforms/video/resize.md)             |
| `VideoFrameRateTransform`   | `frameRate`   | Change frame rate                        | [frame-rate.md](docs/transforms/video/frame-rate.md)     |
| `VideoRemoveAudioTransform` | `removeAudio` | Strip audio track                        | [remove-audio.md](docs/transforms/video/remove-audio.md) |
| `VideoCropTransform`        | `crop`        | Crop frames to sub-region                | [crop.md](docs/transforms/video/crop.md)                 |
| `VideoSpeedTransform`       | `speed`       | Playback speed (adjusts frames + audio)  | [speed.md](docs/transforms/video/speed.md)               |
| `VideoRotateTransform`      | `rotate`      | Rotate by 90°, 180°, or 270°             | [rotate.md](docs/transforms/video/rotate.md)             |

## FFmpeg Configuration

Desktop codecs use a **bundled** FFmpeg by default - zero configuration needed.

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

// Custom transform - no registration needed
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

**Uses [Conventional Commits](**https://www.conventionalcommits.org/) and [release-please](https://github.com/googleapis/release-please) for automated versioning.

## License

MIT License
