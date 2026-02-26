# Transmute

Kotlin Multiplatform media conversion, compression and transformation - image, audio, and video - with a single API across Android, Desktop/JVM, and iOS.

[![JitPack](https://jitpack.io/v/SteveOberst/Transmute.svg)](https://jitpack.io/#SteveOberst/Transmute)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.21-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

## Features

- Single `commonMain` API for image, audio, and video conversion across Android, Desktop (JVM), and iOS
- Native platform codecs by default — no external dependencies for common formats
- Optional `transmute-gstreamer` module fills platform gaps (HEIF/AVIF on Desktop, OGG/Opus on iOS, video on Desktop, etc.)
- Pure-Kotlin WAV and BMP codecs that work on all platforms without native dependencies
- Pipeline-based decode/transform/encode: decode produces an IR, transforms operate on the IR, encode consumes the IR (swap default handlers or build custom typed pipelines)
- Structure reading: parse files into typed Kotlin data classes mirroring on-disk layout (PNG chunks, JPEG segments, RIFF containers, etc.) without decoding pixel/sample data
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
    val format = Transmute.inspect().detectFormat(pngBytes.asBytes())
    if (format == UnknownFormat) error("Could not detect format")

    // Parse file structure without decoding
    val pngStructure: Png = Transmute.structure.read(pngBytes.asBytes(), ImageFormat.Png)
    val roundTripped: Bytes = Transmute.structure.write(pngStructure)
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

See `docs/README.md` for a full index.

- `docs/examples.md` — recipes for image, audio, video, inspect, structure
- `docs/pipelines.md` — typed handler chains, custom decode/encode
- `docs/codec.md` — one-shot decode/encode via `Transmute.codec()`
- `docs/structures.md` — structure reading/writing (parse on-disk layout)
- `docs/inspect.md` — format detection + thumbnail extraction
- `docs/format-detection.md` — per-domain and cross-domain detection
- `docs/extending.md` — custom codecs, transforms & structure readers
- `docs/codecs/` — per-format platform support notes
- `docs/transforms/` — per-transform documentation

## Advanced Pipelines

Transmute uses fluent, pipelines for decode and encode. You can replace either stage entirely.

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

This example chooses PNG when the image is not opaque, otherwise JPEG, unless the caller explicitly forces an output format via `encode { options(...) }`.

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

### Custom Input Type + Custom Decode Pipeline

Decode pipelines are `IN -> Decoded<Format, IR>`. Here we accept a custom input type and map it to raw bytes before using the default decode handler.

```kotlin
class BufferedImageToBytesHandler : PipelineHandler<BufferedImage, Bytes> {
    override suspend fun handle(value: BufferedImage, context: TransmuteContext): Bytes {
        // TODO
    }
}

val fromBufferedImage = Transmute.imageFrom<Bytes> {
    decode {
        options { acceptedInputFormats += setOf(ImageFormat.Jpeg, ImageFormat.Png, ImageFormat.Webp) }

        pipeline(start = BufferedImageToBytesHandler() + ImageCodecs.Decode.DEFAULT)
    }
}
```

## Logging

Transmute uses a structured logging API. By default, logging is set to `INFO` level.

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

## Structure Reading

`Transmute.structure` parses raw file bytes into typed data classes mirroring the on-disk binary layout — without decoding pixel or sample data. Useful for metadata inspection, structural patching, and lossless round-tripping.

```kotlin
// Auto-detect format
val structure = Transmute.structure.read(fileBytes.asBytes())

// Type-safe read with explicit format
val png: Png = Transmute.structure.read(pngBytes.asBytes(), ImageFormat.Png)
val wav: Wav = Transmute.structure.read(wavBytes.asBytes(), AudioFormat.Wav)

// Round-trip (lossless)
val raw: Bytes = Transmute.structure.write(png)
```

Built-in readers: PNG, JPEG, BMP, WAV, MP3, FLAC. Custom readers can be registered for additional formats. See `docs/structures.md` for the full API.

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

Individual modules are also available: `transmute-image`, `transmute-audio`, `transmute-video`, `transmute-structure`.

## Modules

| Module                    | Purpose                                                                             |
|---------------------------|-------------------------------------------------------------------------------------|\n| `transmute-api`           | Public facade — `Transmute` object, `Transformers` factory, DSL extension functions |
| `transmute-common`        | Shared utilities, base types (`Bytes`, `MediaFormat`), pipeline, logging            |
| `transmute-codec`         | Codec infrastructure — registry, encode/decode handler base, format detection       |
| `transmute-model`         | Umbrella for model sub-modules                                                      |
| `transmute-model:core`    | Core model types, `MediaIR`, intermediate representations                           |
| `transmute-model:identify`| Format identification / magic-bytes sniffing                                        |
| `transmute-model:structure`| Binary structure types (`MediaStructure`) mirroring on-disk layout                 |
| `transmute-model:view`    | Read-only & mutable view wrappers over structures (`StructureView`)                 |
| `transmute-model:stream`  | Streaming byte-channel views                                                        |
| `transmute-model:metadata`| Metadata models (EXIF, ID3, Vorbis comments, …)                                    |
| `transmute-model:diagnostics`| Diagnostics & validation helpers                                                 |
| `transmute-filesystem`    | File-system abstraction umbrella                                                    |
| `transmute-filesystem:core`| Core file-system types                                                             |
| `transmute-filesystem:okio`| Okio-backed file-system implementation                                             |
| `transmute-image`         | Image codecs (JPEG, PNG, WebP, HEIF, AVIF, GIF, BMP, TIFF) + transforms            |
| `transmute-audio`         | Audio codecs (WAV, MP3, AAC, FLAC, OGG, Opus, M4A) + transforms                    |
| `transmute-video`         | Video codecs (MP4, MOV, WebM, AVI, MKV) + transforms                               |
| `transmute-structure`     | Structure readers — parse raw bytes into typed `MediaStructure` data classes         |
| `transmute-gstreamer`     | Optional GStreamer-backed codecs — fills platform gaps automatically                |

## Codec Support

Each cell shows what is available **natively** (platform APIs, pure Kotlin, or
JVM libraries — zero extra dependencies). Cells marked **+ gst** gain that
capability when the optional `transmute-gstreamer` module is added.

### Image

| Format                           | Android              | Desktop              | iOS                  | Docs                           |
|----------------------------------|----------------------|----------------------|----------------------|--------------------------------|
| [JPEG](docs/codecs/jpeg.md)      | decode + encode      | decode + encode      | decode + encode      | [jpeg.md](docs/codecs/jpeg.md) |
| [PNG](docs/codecs/png.md)        | decode + encode      | decode + encode      | decode + encode      | [png.md](docs/codecs/png.md)   |
| [WebP](docs/codecs/webp.md)      | decode + encode      | decode + encode      | decode + encode      | [webp.md](docs/codecs/webp.md) |
| [HEIF/HEIC](docs/codecs/heif.md) | decode               | — (+ gst: full)      | decode + encode      | [heif.md](docs/codecs/heif.md) |
| [AVIF](docs/codecs/avif.md)      | decode               | — (+ gst: full)      | decode + encode ¹    | [avif.md](docs/codecs/avif.md) |
| [GIF](docs/codecs/gif.md)        | decode (+ gst: enc)  | decode + encode      | decode + encode      | [gif.md](docs/codecs/gif.md)   |
| [BMP](docs/codecs/bmp.md)        | decode + encode ²    | decode + encode ²    | decode + encode      | [bmp.md](docs/codecs/bmp.md)   |
| [TIFF](docs/codecs/tiff.md)      | decode (+ gst: enc)  | decode + encode      | decode + encode      | [tiff.md](docs/codecs/tiff.md) |

### Audio

| Format                      | Android              | Desktop              | iOS                  | Docs                           |
|-----------------------------|----------------------|----------------------|----------------------|--------------------------------|
| [WAV](docs/codecs/wav.md)   | decode + encode ²    | decode + encode ²    | decode + encode ²    | [wav.md](docs/codecs/wav.md)   |
| [MP3](docs/codecs/mp3.md)   | decode + encode      | decode + encode      | decode (+ gst: enc)  | [mp3.md](docs/codecs/mp3.md)   |
| [AAC](docs/codecs/aac.md)   | decode + encode      | — (+ gst: full)      | decode + encode      | [aac.md](docs/codecs/aac.md)   |
| [M4A](docs/codecs/m4a.md)   | decode + encode      | — (+ gst: full)      | decode + encode      | [m4a.md](docs/codecs/m4a.md)   |
| [FLAC](docs/codecs/flac.md) | decode + encode      | decode (+ gst: enc)  | decode + encode      | [flac.md](docs/codecs/flac.md) |
| [OGG](docs/codecs/ogg.md)   | decode (+ gst: enc)  | decode (+ gst: enc)  | — (+ gst: full)      | [ogg.md](docs/codecs/ogg.md)   |
| [OPUS](docs/codecs/opus.md) | decode + encode ³    | — (+ gst: full)      | — (+ gst: full)      | [opus.md](docs/codecs/opus.md) |

### Video

| Format                      | Android              | Desktop              | iOS                  | Docs                           |
|-----------------------------|----------------------|----------------------|----------------------|--------------------------------|
| [MP4](docs/codecs/mp4.md)   | decode + encode      | — (+ gst: full)      | decode + encode      | [mp4.md](docs/codecs/mp4.md)   |
| [MOV](docs/codecs/mov.md)   | decode + encode      | — (+ gst: full)      | decode + encode      | [mov.md](docs/codecs/mov.md)   |
| [WebM](docs/codecs/webm.md) | decode (+ gst: enc)  | — (+ gst: full)      | — (+ gst: full)      | [webm.md](docs/codecs/webm.md) |
| [AVI](docs/codecs/avi.md)   | — (+ gst: full)      | — (+ gst: full)      | — (+ gst: full)      | [avi.md](docs/codecs/avi.md)   |
| [MKV](docs/codecs/mkv.md)   | — (+ gst: full)      | — (+ gst: full)      | — (+ gst: full)      | [mkv.md](docs/codecs/mkv.md)   |

> ¹ iOS 16+ required. ² Pure Kotlin — works on all platforms, no native dependency. ³ Encode requires Android API 29+.

**Native engines:** Android uses `BitmapFactory` + `MediaCodec`, Desktop uses `ImageIO` + `TwelveMonkeys` + JVM audio libraries, iOS uses `CoreGraphics` + `AVFoundation`. The optional `transmute-gstreamer` module uses GStreamer's plugin framework to fill platform gaps — see [docs/gstreamer.md](docs/gstreamer.md).

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

## GStreamer (Optional)

Formats marked **+ gst** in the codec tables above require a system-installed
[GStreamer](https://gstreamer.freedesktop.org/) runtime and the optional
`transmute-gstreamer` module.

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.github.SteveOberst.Transmute:transmute-gstreamer:<version>")
        }
    }
}
```

Register GStreamer as a supplementary codec provider **once** at application
startup, before any codec operations:

```kotlin
import dev.transmute.gstreamer.GStreamerCodecInstaller

fun main() {
    // GStreamer codecs automatically fill gaps left by native platform codecs.
    // If GStreamer is not installed on the system, the calls are safe no-ops.
    GStreamerCodecInstaller.registerAsSupplementary()

    // Now all codec operations transparently use GStreamer where needed:
    //   Audio  – AAC, M4A, Opus, FLAC encode, OGG encode
    //   Image  – HEIF, HEIC, AVIF
    //   Video  – MP4, MOV, WebM, AVI, MKV
}
```

You can also install GStreamer codecs explicitly into specific registries:

```kotlin
GStreamerCodecInstaller.installAudioCodecs(AudioRegistries.decoders, AudioRegistries.encoders)
GStreamerCodecInstaller.installImageCodecs(ImageRegistries.decoders, ImageRegistries.encoders)
GStreamerCodecInstaller.installVideoCodecs(VideoRegistries.decoders, VideoRegistries.encoders)
```

GStreamer is detected automatically via the system PATH. On Desktop/JVM, codecs
run as `gst-launch-1.0` subprocesses. On Android, GStreamer is invoked via JNI
(`libgstreamer_bridge.so`). On iOS, GStreamer is invoked via cinterop
(`GStreamer.framework`).

On Windows, the `GSTREAMER_1_0_ROOT_MSVC_X86_64` /
`GSTREAMER_1_0_ROOT_X86_64` environment variables and common install paths are
also checked.

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
