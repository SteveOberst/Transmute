# Examples

These examples focus on end-to-end usage of transmuters (decode -> transforms -> encode), plus a few inspection/codec patterns that are commonly useful in real apps.

## Image recipes

```kotlin
// imports omitted

// Make a square thumbnail and force JPEG output
val thumbJpeg =
  Transmute.image {
    crop(x = 0, y = 0, width = 512, height = 512)
    scale(maxWidth = 256, maxHeight = 256)
    encode { options(JpegEncodeOptions(quality = 0.85f)) }
  }

// Preserve metadata (encode concern)
val keepMetadata =
  Transmute.image {
    encode { options { metadataPolicy = dev.transmute.core.MetadataPolicy.PRESERVE } }
  }

// Fixed output at the type level (useful for type-safe post-encode handlers)
val pngOnly =
  Transmute.image.to(ImageFormat.Png) {
    encode { options { outputFormat = OutputFormat.Exact(ImageFormat.Png) } }
  }
```

## Image: dynamic output format selection (smart JPEG vs PNG)

This is a "show the pipeline" example: you can override the default encode stage and decide an output format at runtime (while still keeping output format selection an *encode* concern).

```kotlin
// imports omitted

val smartThumb =
  Transmute.image {
    scale(maxWidth = 512, maxHeight = 512)

    encode {
      options {
        outputFormat = OutputFormat.ORIGINAL
        metadataPolicy = dev.transmute.core.MetadataPolicy.STRIP_ALL
      }

      pipeline(
        initial =
          ImageDynamicEncodeHandler(
            outputFormatSelector =
              ImageOutputFormatSelector { decoded, options ->
                when (val requested = options.outputFormat) {
                  OutputFormat.ORIGINAL ->
                    if (decoded.ir.alphaSemantics != AlphaSemantics.OPAQUE) ImageFormat.Png else ImageFormat.Jpeg
                  is OutputFormat.Exact -> requested.format
                }
              },
          ) + tap { out, ctx ->
            ctx.logger.debug("encoded ${out.format} -> ${out.bytes.data.size} bytes")
          },
      )
    }
  }
```

## Audio recipes

```kotlin
// imports omitted

// Normalize + trim and force AAC output
val aac =
  Transmute.audio {
    normalize(targetPeak = 0.9f)
    trim(startMs = 1_000, endMs = 5_000)
    encode { options { outputFormat = OutputFormat.Exact(AudioFormat.Aac) } }
  }
```

## Audio: preserve metadata and keep original format when possible

```kotlin
// imports omitted

val clean =
  Transmute.audio {
    normalize(targetPeak = 0.9f)
    trim(startMs = 1_000, endMs = 5_000)
    fade(fadeInMs = 50, fadeOutMs = 100)

    encode {
      options {
        outputFormat = OutputFormat.ORIGINAL
        metadataPolicy = dev.transmute.core.MetadataPolicy.PRESERVE
      }
    }
  }
```

## Video recipes

```kotlin
// imports omitted

// Make a silent MP4 preview clip
val preview =
  Transmute.video {
    trim(startMs = 0, endMs = 10_000)
    removeAudio()
    encode { options { outputFormat = OutputFormat.Exact(VideoFormat.Mp4) } }
  }
```

## Inspect: extract a thumbnail from video

```kotlin
// imports omitted

suspend fun extract(videoBytes: ByteArray) {
  val thumbnailPng =
    Transmute.inspect().video.thumbnailFirstFrame(
      videoBytes.asBytes(),
      imageEncodeOptions = PngEncodeOptions(),
      // Optionally provide a DecodeRange for a specific timestamp.
    )

  val thumbBytes = thumbnailPng.bytes.data
}
```

## Structure: read a file without decoding

```kotlin
// imports omitted

// Auto-detect format
val structure = Transmute.structure.read(pngBytes.asBytes())

// Type-safe read with explicit format
val png: Png = Transmute.structure.read(pngBytes.asBytes(), ImageFormat.Png)
val wav: Wav = Transmute.structure.read(wavBytes.asBytes(), AudioFormat.Wav)

// Round-trip: read -> write (lossless)
val original = fileBytes
val structure = Transmute.structure.read(original.asBytes())
val roundTripped = Transmute.structure.write(structure)
// roundTripped.data contentEquals original  →  true
```

## Structure: write to a sink

```kotlin
// imports omitted

val sink = BytesSink()
Transmute.structure.writeTo(pngStructure, sink)
val raw: Bytes = sink.collect()
```
