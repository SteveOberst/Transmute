# Pipelines (vNext)

Transmute models **decode**, **transform**, and **encode** as *typed handler chains*.

- Decode stage returns `Decoded<F, IR>` (resolved input format + intermediate representation).
- Transform stage operates on the IR only.
- Encode stage chooses an output format via `EncodeOptions` and typically produces `EncodedBytes` (but the pipeline can continue with post-encode steps and return any type).

## Default Stages (class-first)

The default implementations are regular handler classes you can reuse in your own pipelines:

- Images: `ImageDecodeHandler`, `ImageDynamicEncodeHandler`, `ImageFixedEncodeHandler`
- Audio: `AudioDecodeHandler`, `AudioDynamicEncodeHandler`, `AudioFixedEncodeHandler`
- Video: `VideoDecodeHandler`, `VideoDynamicEncodeHandler`, `VideoFixedEncodeHandler`

## Prefer handler classes (not lambdas)

Most real-world decode/encode steps map cleanly to handler classes (including codecs). Transmute provides overloads that let you add domain decoders/encoders to a pipeline directly:

```kotlin
fun buildJpegDecodePipeline(): DecodePipeline<Bytes, Decoded<ImageFormat, ImageIR>> {
  ImageRegistries.installDefaultsIfEmpty()
  val jpegDecoder = ImageRegistries.decoders.decoderFor(ImageFormat.Jpeg) ?: error("No JPEG decoder registered")

  return PipelineBuilder
    .start<Bytes>()
    .then(jpegDecoder) // resolves format using acceptedInputFormats / sniff / detector
    .build()
}

fun buildPngEncodePipeline(): EncodePipeline<Decoded<ImageFormat, ImageIR>, EncodedBytes<ImageFormat>> {
  ImageRegistries.installDefaultsIfEmpty()
  val pngEncoder = ImageRegistries.encoders.encoderFor(ImageFormat.Png) ?: error("No PNG encoder registered")

  return PipelineBuilder
    .start<Decoded<ImageFormat, ImageIR>>()
    .then(pngEncoder, output = ImageFormat.Png)
    .build()
}
```

This keeps encode/decode “class-first” while still allowing fully typed pipelines.

## Platform-Native Inputs + Default Decode Handler

In real apps, you often start with a platform-native image object (Android `Bitmap`, iOS `UIImage`, Desktop/JVM `BufferedImage`).
You can map it to raw `Bytes` and then reuse `ImageCodecs.Decode.DEFAULT`.

These handlers must live in the corresponding source set (`androidMain`, `iosMain`, `desktopMain`), since the platform types are not available in `commonMain`.

### Android (`Bitmap`)

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

val t =
  Transmute.image.custom.from<android.graphics.Bitmap> {
    decode { pipeline(initial = BitmapToBytesHandler() + ImageCodecs.Decode.DEFAULT) }
  }
```

### iOS (`UIImage`)

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

val t =
  Transmute.image.custom.from<platform.UIKit.UIImage> {
    decode {
      options { acceptedInputFormats += setOf(ImageFormat.Png) } // skip detection (we always emitted PNG)
      pipeline(initial = UIImageToBytesHandler() + ImageCodecs.Decode.DEFAULT)
    }
  }
```

### Desktop/JVM (`BufferedImage`)

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

val t =
  Transmute.image.custom.from<java.awt.image.BufferedImage> {
    decode {
      options { acceptedInputFormats += setOf(ImageFormat.Png) } // if you always write PNG above
      pipeline(initial = BufferedImageToBytesHandler("png") + ImageCodecs.Decode.DEFAULT)
    }
  }
```

## Dynamic Encode Format Selection

Output format selection is an **encode concern** (via `encode { options { ... } }` or a handler policy),
not a builder-level knob.

```kotlin
val t = Transmute.image {
  encode {
    options { outputFormat = OutputFormat.ORIGINAL }

    pipeline(
      initial =
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

## Platform-Native Outputs (post-encode handlers)

Encode pipelines are handler chains too, and they can change types. The examples below build transmuters that return platform-native output objects directly.

### Android: `EncodedBytes` -> `Bitmap`

```kotlin
class EncodedBytesToBitmapHandler : PipelineHandler<EncodedBytes<ImageFormat>, android.graphics.Bitmap> {
  override suspend fun handle(value: EncodedBytes<ImageFormat>, context: TransmuteContext): android.graphics.Bitmap {
    val data = value.bytes.data
    return android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size)
      ?: error("BitmapFactory.decodeByteArray returned null")
  }
}

val t =
  Transmute.image.custom.out<android.graphics.Bitmap> {
    encode {
      options { outputFormat = OutputFormat.Exact(ImageFormat.Jpeg) }
      pipeline(initial = ImageCodecs.Encode.DEFAULT + EncodedBytesToBitmapHandler())
    }
  }
```

### iOS: `EncodedBytes` -> `UIImage`

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

val t =
  Transmute.image.custom.out<platform.UIKit.UIImage> {
    encode {
      options { outputFormat = OutputFormat.Exact(ImageFormat.Jpeg) }
      pipeline(initial = ImageCodecs.Encode.DEFAULT + EncodedBytesToUIImageHandler())
    }
  }
```

### Desktop/JVM: `EncodedBytes` -> `BufferedImage`

```kotlin
class EncodedBytesToBufferedImageHandler : PipelineHandler<EncodedBytes<ImageFormat>, java.awt.image.BufferedImage> {
  override suspend fun handle(value: EncodedBytes<ImageFormat>, context: TransmuteContext): java.awt.image.BufferedImage {
    val input = java.io.ByteArrayInputStream(value.bytes.data)
    return javax.imageio.ImageIO.read(input) ?: error("ImageIO.read returned null")
  }
}

val t =
  Transmute.image.custom.out<java.awt.image.BufferedImage> {
    encode {
      options { outputFormat = OutputFormat.Exact(ImageFormat.Jpeg) }
      pipeline(initial = ImageCodecs.Encode.DEFAULT + EncodedBytesToBufferedImageHandler())
    }
  }
```

## Handler Composition (operator `+`)

Handlers compose via `+`:

```kotlin
val encode =
  ImageCodecs.Encode.DEFAULT +
    MyEncodeAuditHandler() +
    MyMetricsHandler()
```

If you want to append *same-type* post steps, `+=` works because the handler type stays the same:

```kotlin
var encode = ImageCodecs.Encode.DEFAULT
encode += tap { out, ctx -> ctx.logger.info("encoded ${out.format} -> ${out.bytes.size} bytes") }
```

The same idea applies to *pipeline builders* (as long as the step does not change the current type):

```kotlin
var decode = PipelineBuilder.start<Bytes>().startWith(ImageCodecs.Decode.DEFAULT)
decode += tap { decoded, ctx -> ctx.logger.info("decoded ${decoded.format}") }
val pipeline = decode.build()
```

## Codec Registration & Plugins

Pipeline handlers rely on codecs from the registries. Codecs can be registered:

- **Directly** via `ImageRegistries.register(...)`, `AudioRegistries.register(...)`, etc.
- **Via plugins** using the `Transmute { plugins { install(...) } }` builder DSL.

When using the instance-based API, each `Transmute` instance gets its own registries populated
by installed plugins. See [plugins.md](plugins.md) for details.

## See Also

- [codec.md](codec.md) — decode, encode, and format detection facade
- [extending.md](extending.md) — custom codecs, transforms, and structure readers
- [plugins.md](plugins.md) — instance-based API and plugin system
- Module READMEs: [transmute-codec](../transmute-codec/README.md),
  [transmute-api](../transmute-api/README.md)
