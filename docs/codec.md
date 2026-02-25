# `Transmute.codec()`

`Transmute.codec()` is the codec facade: **decode**, **encode**, format detection, and range decode (audio/video).

It is a lightweight singleton. Each call creates a fresh `TransmuteContext` internally, so you can pass different decode/encode options per call.

If you want to build and reuse the *default* codec pipelines directly, each domain exposes:

- `defaultDecoder()` / `defaultEncoder()` (configured with canonical options)
- `decoder { ... }` / `encoder { ... }` (build a custom Bytes-in decoder or IR-in encoder pipeline)

You can also embed these pipelines into a transmuter stage:

```kotlin
// imports omitted

val codec = Transmute.codec()

val t = Transmute.image.from<java.awt.image.BufferedImage> {
    decode {
      pipeline(initial = BufferedImageToBytesHandler("png") + codec.image.defaultDecoder().pipeline)
    }
    encode {
      pipeline(initial = codec.image.defaultEncoder().pipeline + EncodedBytesToBufferedImageHandler())
    }
  }
```

## Example

```kotlin
// imports omitted

suspend fun codecExample(bytes: ByteArray) {
  val codec = Transmute.codec()

  // Decode with per-call options
  val decoded =
    codec.image.decode(
      source = bytes.asBytes(),
      options =
        CanonicalImageDecodeOptions(
          acceptedInputFormats = setOf(ImageFormat.Jpeg, ImageFormat.Png),
        ),
    )

  // Encode with per-call options
  val out =
    codec.image.encode(
      decoded = decoded,
      options = JpegEncodeOptions(quality = 0.9f, metadataPolicy = dev.transmute.core.MetadataPolicy.PRESERVE),
    )

  val outBytes = out.bytes.data
}
```

## Range decode (audio/video)

Audio/video decode options support `decodeRange`, which is a `DecodeRange` request.

Built-in implementations include:
- `TimeRangeMs(startMs, endMsExclusive)` (all domains)
- `FrameIndexRange(startFrameIndex, endFrameIndexExclusive, frameRate)` (typically video; converted to a time range)

If the active decoder cannot satisfy the requested range efficiently, it throws `UnsupportedOperationException` (never silently ignores the request).
