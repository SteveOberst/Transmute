# Transmute

Kotlin Multiplatform library offering hardware-accelerated media transcoding and transformation via a format-agnostic intermediate representation - Image, Audio, and Video, with a single API across Android, Desktop/JVM, and iOS.

[![JitPack](https://jitpack.io/v/SteveOberst/Transmute.svg)](https://jitpack.io/#SteveOberst/Transmute)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.21-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

## Features

- Single `commonMain` API for image, audio, and video conversion across Android, Desktop (JVM), and iOS
- Bundled FFmpeg for desktop - no external install required
- Pure-Kotlin WAV and BMP codecs that work on all platforms without native dependencies
- Pipeline-based decode/transform/encode: decode produces an IR, transforms operate on the IR, encode consumes the IR (swap default handlers or build custom typed pipelines)
- Image resample filters: Nearest, Bilinear, Mitchell, Catmull-Rom, Lanczos3 (with anti-alias for downscale)
- 27 transforms across all three media types (scale, crop, rotate, blur, normalize, trim, fade, gain, speed, compressor, etc.)
- Configurable logging with level filtering and pluggable logger backends

## Quick Start

```kotlin       
suspend fun quickStart(
    pngBytes: ByteArray,
    wavBytes: ByteArray,
    mp4Bytes: ByteArray,
) {
    // Transmute uses `Bytes` as the canonical binary type.
    // Use `ByteArray.asBytes()` when your inputs are raw byte arrays.

    // Scale and convert to JPEG
    val jpegBytes = Transmute.image {
        scale(maxWidth = 1920, maxHeight = 1080)
        encode { options(JpegEncodeOptions(quality = 0.85f)) }
    }.transmute(pngBytes.asBytes()).bytes.data

    // Resize to exact dimensions with Lanczos resampling
    val resizedBytes = Transmute.image {
        resize(800, 600, filter = ResampleFilter.LANCZOS3)
    }.transmute(pngBytes.asBytes()).bytes.data

    // Normalize and trim audio (preserves input format if encodable, otherwise falls back to WAV)
    val audioBytes = Transmute.audio {
        normalize(targetPeak = 0.9f)
        trim(startMs = 1000, endMs = 5000)
        fade(fadeInMs = 100, fadeOutMs = 200)
    }.transmute(wavBytes.asBytes()).bytes.data

    // Resize video and force output format via encode options
    val videoBytes = Transmute.video {
        resize(maxWidth = 1280, maxHeight = 720)
        trim(startMs = 0, endMs = 30_000)
        encode { options { outputFormat = OutputFormat.Exact(VideoFormat.Mp4) } }
    }.transmute(mp4Bytes.asBytes()).bytes.data

    // Detect format from raw bytes
    val format = Transmute.detectFormat(pngBytes.asBytes())
    if (format == UnknownFormat) error("Could not detect format")
}
```

## Building Transmuters

Transmuters are reusable objects you build once and apply to many inputs.
If your inputs are `ByteArray`, pass `bytes.asBytes()` to `transmute(...)`.

```kotlin
// Reusable dynamic-output image transmuter (output defaults to "same as input" unless encode options force it)
val thumbnailer = Transmute.image {
    decode { options { acceptedInputFormats += setOf(ImageFormat.Png, ImageFormat.Jpeg, ImageFormat.Webp) } } // optional
    scale(maxWidth = 512, maxHeight = 512)
    encode { options(JpegEncodeOptions(quality = 0.85f)) }
}

suspend fun makeThumb(bytes: ByteArray): ByteArray =
  thumbnailer.transmute(bytes.asBytes()).bytes.data
```

Fixed-output transmuters expose a type-level output format object (useful for type-safe post-encode handlers in custom encode pipelines):

```kotlin
val pngOnly = Transmute.imageTo(ImageFormat.Png) {
    encode { options(PngEncodeOptions(compressionLevel = 6)) }
}

suspend fun toPng(bytes: ByteArray): ByteArray =
  pngOnly.transmute(bytes.asBytes()).bytes.data
```

Decode pipelines are generic over `IN`, so you can accept custom inputs by supplying your own decode stage:
See “Advanced Pipelines” below for a full example.

## Docs

- `docs/pipelines.md`
- `docs/format-detection.md`
- `docs/codecs/`
- `docs/transforms/`

## Advanced Pipelines

Transmute uses fluent, pipelines for decode and encode. You can replace either stage entirely.

### Common Recipes

```kotlin
suspend fun commonRecipes(
    inputImage: ByteArray,
    inputAudio: ByteArray,
    inputVideo: ByteArray,
) {
    // 1) Make a square thumbnail and force JPEG output
    val thumbJpeg =
      Transmute.image {
        crop(x = 0, y = 0, width = 512, height = 512)
        scale(maxWidth = 256, maxHeight = 256)
        encode { options(JpegEncodeOptions(quality = 0.85f)) }
      }.transmute(inputImage.asBytes()).bytes.data

    // 2) Preserve metadata while changing output format dynamically
    val keepMetadata =
      Transmute.image {
        encode { options { metadataPolicy = MetadataPolicy.PRESERVE } }
      }.transmute(inputImage.asBytes()).bytes.data

    // 3) Normalize + trim audio and force AAC output
    val aac =
      Transmute.audio {
        normalize(targetPeak = 0.9f)
        trim(startMs = 1_000, endMs = 5_000)
        encode { options { outputFormat = OutputFormat.Exact(AudioFormat.Aac) } }
      }.transmute(inputAudio.asBytes()).bytes.data

    // 4) Make a silent MP4 preview clip
    val preview =
      Transmute.video {
        resize(maxWidth = 1280, maxHeight = 720)
        trim(startMs = 0, endMs = 15_000)
        removeAudio()
        encode { options { outputFormat = OutputFormat.Exact(VideoFormat.Mp4) } }
      }.transmute(inputVideo.asBytes()).bytes.data
}
```

### Format-Specific Encode Options

Some formats expose dedicated encode options types:

```kotlin
suspend fun encodeOptionsExamples(inputBytes: ByteArray) {
    val jpeg = Transmute.image {
        encode { options(JpegEncodeOptions(quality = 0.9f, metadataPolicy = MetadataPolicy.PRESERVE)) }
    }.transmute(inputBytes.asBytes()).bytes.data

    val webpLossless = Transmute.image {
        encode { options(WebPEncodeOptions(lossless = true)) }
    }.transmute(inputBytes.asBytes()).bytes.data

    val avif = Transmute.image {
        encode { options(HeifEncodeOptions(format = ImageFormat.Avif, quality = 0.8f)) }
    }.transmute(inputBytes.asBytes()).bytes.data
}
```

### Dynamic Encode Pipeline (choose output format at runtime)

This example chooses PNG when the image is not opaque, otherwise JPEG, unless the caller explicitly forces an output format via `encode { options { outputFormat = ... } }`.

```kotlin
val smartOutput = Transmute.image {
    encode {
        options { outputFormat = OutputFormat.ORIGINAL }

        pipeline(
          start =
            ImageDynamicEncodeHandler(
              outputFormatSelector = ImageOutputFormatSelector { decoded, options ->
                when (val requested = options.outputFormat) {
                  OutputFormat.ORIGINAL ->
                    if (decoded.ir.alphaSemantics != AlphaSemantics.OPAQUE) ImageFormat.Png else ImageFormat.Jpeg
                  is OutputFormat.Exact -> requested.format
                }
              },
            ) + tap { out, ctx ->
              ctx.logger.info("encoded ${out.format} -> ${out.bytes.size} bytes")
            },
        )
    }
}
```

### Platform-Native Inputs + Custom Decode Pipelines

Decode pipelines are `IN -> Decoded<F, IR>`. A common real-world use case is starting from a platform-native image type and mapping it to raw `Bytes` so you can reuse Transmute’s default decode handler.

#### Android: `Bitmap` input

```kotlin
class BitmapToBytesHandler(
  private val compressFormat: android.graphics.Bitmap.CompressFormat = android.graphics.Bitmap.CompressFormat.PNG,
  private val quality: Int = 100,
) : PipelineHandler<android.graphics.Bitmap, Bytes> {
  override suspend fun handle(value: android.graphics.Bitmap, context: TransmuteContext): Bytes {
    val out = java.io.ByteArrayOutputStream()
    val ok = value.compress(compressFormat, quality, out)
    require(ok) { "Bitmap.compress failed (format=$compressFormat)" }
    return out.toByteArray().asBytes()
  }
}

val fromBitmap =
  Transmute.imageFrom<android.graphics.Bitmap> {
    decode { pipeline(start = BitmapToBytesHandler() + ImageCodecs.Decode.DEFAULT) }
    scale(maxWidth = 1024, maxHeight = 1024)
    encode { options(JpegEncodeOptions(quality = 0.85f)) }
  }
```

#### iOS: `UIImage` input

```kotlin
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

class UIImageToBytesHandler : PipelineHandler<platform.UIKit.UIImage, Bytes> {
  override suspend fun handle(value: platform.UIKit.UIImage, context: TransmuteContext): Bytes {
    val data = platform.UIKit.UIImagePNGRepresentation(value) ?: error("UIImagePNGRepresentation returned null")
    val size = data.length.toInt()
    val bytes = ByteArray(size)
    bytes.usePinned { pinned ->
      platform.posix.memcpy(pinned.addressOf(0), data.bytes, size.toULong())
    }
    return bytes.asBytes()
  }
}

val fromUIImage =
  Transmute.imageFrom<platform.UIKit.UIImage> {
    decode {
      options { acceptedInputFormats += setOf(ImageFormat.Png) } // skip detection (we always emitted PNG)
      pipeline(start = UIImageToBytesHandler() + ImageCodecs.Decode.DEFAULT)
    }
    scale(maxWidth = 1024, maxHeight = 1024)
    encode { options(JpegEncodeOptions(quality = 0.85f)) }
  }
```

#### Desktop/JVM: `BufferedImage` input

```kotlin
class BufferedImageToBytesHandler(
  private val formatName: String = "png",
) : PipelineHandler<java.awt.image.BufferedImage, Bytes> {
  override suspend fun handle(value: java.awt.image.BufferedImage, context: TransmuteContext): Bytes {
    val out = java.io.ByteArrayOutputStream()
    val ok = javax.imageio.ImageIO.write(value, formatName, out)
    require(ok) { "No ImageIO writer for formatName=$formatName" }
    return out.toByteArray().asBytes()
  }
}

val fromBufferedImage =
  Transmute.imageFrom<java.awt.image.BufferedImage> {
    decode {
      options { acceptedInputFormats += setOf(ImageFormat.Png) } // if you always write PNG above
      pipeline(start = BufferedImageToBytesHandler("png") + ImageCodecs.Decode.DEFAULT)
    }
    scale(maxWidth = 1024, maxHeight = 1024)
    encode { options(JpegEncodeOptions(quality = 0.85f)) }
  }
```

### Platform-Native Outputs (post-encode handlers)

Encode pipelines are handler chains too, and they can change types. The examples below build transmuters that return platform-native output objects directly.

#### Android: convert encoded bytes to `Bitmap`

```kotlin
class EncodedBytesToBitmapHandler : PipelineHandler<EncodedBytes<ImageFormat>, android.graphics.Bitmap> {
  override suspend fun handle(value: EncodedBytes<ImageFormat>, context: TransmuteContext): android.graphics.Bitmap {
    val data = value.bytes.data
    return android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size)
      ?: error("BitmapFactory.decodeByteArray returned null")
  }
}

val toBitmap =
  Transmute.imageOut<android.graphics.Bitmap> {
    encode {
      options { outputFormat = OutputFormat.Exact(ImageFormat.Jpeg) }
      pipeline(start = ImageCodecs.Encode.DEFAULT + EncodedBytesToBitmapHandler())
    }
  }
```

#### iOS: convert encoded bytes to `UIImage`

```kotlin
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

class EncodedBytesToUIImageHandler : PipelineHandler<EncodedBytes<ImageFormat>, platform.UIKit.UIImage> {
  override suspend fun handle(value: EncodedBytes<ImageFormat>, context: TransmuteContext): platform.UIKit.UIImage {
    val bytes = value.bytes.data
    val data = bytes.usePinned { pinned ->
      platform.Foundation.NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
    }
    return platform.UIKit.UIImage.imageWithData(data) ?: error("UIImage.imageWithData returned null")
  }
}

val toUIImage =
  Transmute.imageOut<platform.UIKit.UIImage> {
    encode {
      options { outputFormat = OutputFormat.Exact(ImageFormat.Jpeg) }
      pipeline(start = ImageCodecs.Encode.DEFAULT + EncodedBytesToUIImageHandler())
    }
  }
```

#### Desktop/JVM: convert encoded bytes to `BufferedImage`

```kotlin
class EncodedBytesToBufferedImageHandler : PipelineHandler<EncodedBytes<ImageFormat>, java.awt.image.BufferedImage> {
  override suspend fun handle(value: EncodedBytes<ImageFormat>, context: TransmuteContext): java.awt.image.BufferedImage {
    val input = java.io.ByteArrayInputStream(value.bytes.data)
    return javax.imageio.ImageIO.read(input) ?: error("ImageIO.read returned null")
  }
}

val toBufferedImage =
  Transmute.imageOut<java.awt.image.BufferedImage> {
    encode {
      options { outputFormat = OutputFormat.Exact(ImageFormat.Jpeg) }
      pipeline(start = ImageCodecs.Encode.DEFAULT + EncodedBytesToBufferedImageHandler())
    }
  }
```

## Logging

Transmute uses a structured logging API. By default, logging is set to `WARN` level.

```kotlin

// Silence all logging
TransmuteLogging.configure(LogLevel.OFF)

// Only warnings and errors
TransmuteLogging.configure(LogLevel.WARN)

// Debug-level (verbose)
TransmuteLogging.configure(LogLevel.DEBUG)

// Supply a custom logger backend
TransmuteLogging.configure(LogLevel.INFO, myConversionLogger)

// Per-operation logger override
suspend fun withCustomLogger(bytes: ByteArray) {
    val out = Transmute.image {
        logger(myLogger)
        scale(maxWidth = 800, maxHeight = 600)
    }.transmute(bytes.asBytes())
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

| Module            | Purpose                                                                             |
|-------------------|-------------------------------------------------------------------------------------|
| `transmute-api`   | Public facade - `Transmute` object, `Transformers` factory, DSL extension functions |
| `transmute-core`  | Base types (`Bytes`, `MediaFormat`, codecs), pipeline, logging, `TransmuteConfig`   |
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
class MyWebpDecoder : ImageDecoder {
    override val supportedFormats = setOf(ImageFormat.Webp)
    override fun sniff(data: Bytes): ImageFormat? = /* magic bytes */ null
    override suspend fun decode(source: Bytes, options: ImageDecodeOptions, context: TransmuteContext): ImageIR = TODO()
}

class MyWebpEncoder : ImageEncoder {
    override val supportedFormats = setOf(ImageFormat.Webp)
    override suspend fun encode(
        ir: ImageIR,
        format: ImageFormat,
        options: ImageEncodeOptions,
        context: TransmuteContext,
    ): Bytes = TODO()
}
ImageRegistries.register(MyWebpDecoder())
ImageRegistries.register(MyWebpEncoder())

// Custom transform - no registration needed
class WatermarkTransform(private val logo: ByteArray) : Transform<ImageIR> {
    override val id = TransformId("image.watermark")
    override suspend fun apply(ir: ImageIR, ctx: TransmuteContext): ImageIR { /* ... */ }
}

suspend fun applyWatermark(photo: ByteArray): ByteArray =
  Transmute.image {
    transform { add(WatermarkTransform(logoPng)) }
  }.transmute(photo.asBytes()).bytes.data
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for setup instructions, coding conventions,
and how to add codecs or transforms.

**Uses [Conventional Commits](https://www.conventionalcommits.org/) and [release-please](https://github.com/googleapis/release-please) for automated versioning.**

## License

MIT License
