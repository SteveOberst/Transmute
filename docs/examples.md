# Examples

These examples focus on end-to-end usage of transmuters (decode -> transforms -> encode), plus a few inspection/codec patterns that are commonly useful in real apps.

## Image recipes

```kotlin
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
    encode { options { metadataPolicy = dev.transmute.codec.MetadataPolicy.PRESERVE } }
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
val smartThumb =
  Transmute.image {
    scale(maxWidth = 512, maxHeight = 512)

    encode {
      options {
        outputFormat = OutputFormat.ORIGINAL
        metadataPolicy = dev.transmute.codec.MetadataPolicy.STRIP_ALL
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
val clean =
  Transmute.audio {
    normalize(targetPeak = 0.9f)
    trim(startMs = 1_000, endMs = 5_000)
    fade(fadeInMs = 50, fadeOutMs = 100)

    encode {
      options {
        outputFormat = OutputFormat.ORIGINAL
        metadataPolicy = dev.transmute.codec.MetadataPolicy.PRESERVE
      }
    }
  }
```

## Video recipes

```kotlin
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
suspend fun extract(videoBytes: ByteArray) {
  val thumbnailPng =
    Transmute.inspect.video.thumbnailFirstFrame(
      videoBytes.asBytes(),
      imageEncodeOptions = PngEncodeOptions(),
      // Optionally provide a DecodeRange for a specific timestamp.
    )

  val thumbBytes = thumbnailPng.bytes.data
}
```

## Structure: read a file without decoding

```kotlin
// Auto-detect format
val structure = Transmute.structure.read(pngBytes.asBytes())

// Type-safe read with explicit format
val png: Png = Transmute.structure.read(pngBytes.asBytes(), ImageFormat.Png)
val wav: Wav = Transmute.structure.read(wavBytes.asBytes(), AudioFormat.Wav)

// Round-trip: read -> write (lossless)
val original = fileBytes
val structure = Transmute.structure.read(original.asBytes())
val roundTripped = Transmute.structure.write(structure)
// roundTripped.data contentEquals original  ->  true
```

## Structure: lambda sugar

The `read` overloads accept a trailing lambda where the parsed structure is the receiver:

```kotlin
// Extract a single field without a local variable
val width: Int = Transmute.structure.read<Png>(pngBytes.asBytes(), ImageFormat.Png) {
    ihdr.width.toInt()
}

// Check audio properties in one expression
val isStereo = Transmute.structure.read<Wav>(wavBytes.asBytes(), AudioFormat.Wav) {
    fmt.numChannels == 2.toUShort()
}
```

## Structure: read from a TSource (suspending)

When working with files, network streams, or any `TSource`, the suspending overloads
read the bytes asynchronously:

```kotlin
suspend fun inspectPng(source: TSource) {
    val png: Png = Transmute.structure.read(source, ImageFormat.Png)
    println("Image: ${png.ihdr.width} × ${png.ihdr.height}")
}

// Lambda sugar also works with TSource
suspend fun pngWidth(source: TSource): Int =
    Transmute.structure.read<Png>(source, ImageFormat.Png) { ihdr.width.toInt() }
```

## Structure: write to a TSink

```kotlin
suspend fun writePng(structure: Png, sink: TSink) {
    Transmute.structure.writeTo(structure, sink)
}

// In-memory sink
val sink = ByteArraySink()
Transmute.structure.writeTo(pngStructure, sink)
val raw: ByteArray = sink.collect()
```

## Structure: in-place transform via TChannel

Read, modify, and write back through a single channel:

```kotlin
suspend fun stampPng(channel: TChannel) {
    Transmute.structure.transform<Png>(channel, ImageFormat.Png) {
        // 'this' is Png - return a modified copy
        copy(textChunks = textChunks + TextChunk("Comment", "Processed"))
    }
}
```

See [structures.md](structures.md) for the full structure API,
including IO abstractions (`TSource`, `TSink`, `TChannel`).

## Instance-based API with plugins

All examples above use the static `Transmute.xxx` API. For plugin-based
setups, create an instance with `Transmute { }`:

```kotlin
// All features enabled by default
val transmute = Transmute {
    plugins {
    install(GStreamer)  // audio + video codecs
    install(LibHeif)    // HEIF/HEIC/AVIF image codecs on Desktop
    }
}

// Use the instance exactly like the static API
val heif = transmute.image {
    scale(maxWidth = 512, maxHeight = 512)
}.transmute(inputBytes.asBytes())

val mp4: Mp4 = transmute.structure.read(mp4Bytes.asBytes(), VideoFormat.Mp4)
```

See [plugins.md](plugins.md) for the full plugin system documentation.
