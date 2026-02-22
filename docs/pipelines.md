# Pipelines (vNext)

Transmute models **decode**, **transform**, and **encode** as *typed handler chains*.

- Decode stage returns `Decoded<F, IR>` (resolved input format + intermediate representation).
- Transform stage operates on the IR only.
- Encode stage chooses an output format via `EncodeOptions` and produces `EncodedBytes`.

## Default Stages (class-first)

The default implementations are regular handler classes you can reuse in your own pipelines:

- Images: `ImageDecodeHandler`, `ImageDynamicEncodeHandler`, `ImageFixedEncodeHandler`
- Audio: `AudioDecodeHandler`, `AudioDynamicEncodeHandler`, `AudioFixedEncodeHandler`
- Video: `VideoDecodeHandler`, `VideoDynamicEncodeHandler`, `VideoFixedEncodeHandler`

## Custom Input Type + Default Decode Handler

```kotlin
import dev.transmute.Transmute
import dev.transmute.core.OutputFormat
import dev.transmute.core.asBytes
import dev.transmute.image.CanonicalImageDecodeOptions
import dev.transmute.image.ImageFormat
import dev.transmute.image.ImageDecodeHandler

data class NamedBytes(val name: String, val bytes: ByteArray)

val t = Transmute.imageFrom<NamedBytes> {
  decodeOptions(CanonicalImageDecodeOptions(acceptedInputFormats = setOf(ImageFormat.Png, ImageFormat.Jpeg)))

  decode {
    startWith { input, _ -> input.bytes.asBytes() }
      .then(ImageDecodeHandler())
  }
}
```

## Dynamic Encode Format Selection

Output format selection is an **encode concern** (via `encodeOptions.outputFormat` or a handler policy),
not a builder-level knob.

```kotlin
import dev.transmute.Transmute
import dev.transmute.core.OutputFormat
import dev.transmute.image.AlphaSemantics
import dev.transmute.image.CanonicalImageEncodeOptions
import dev.transmute.image.ImageFormat
import dev.transmute.image.ImageDynamicEncodeHandler
import dev.transmute.image.ImageOutputFormatSelector

val t = Transmute.image {
  encodeOptions(CanonicalImageEncodeOptions(outputFormat = OutputFormat.ORIGINAL))

  encode {
    startWith(
      ImageDynamicEncodeHandler(
        outputFormatSelector = ImageOutputFormatSelector { decoded, options ->
          when (val requested = options.outputFormat) {
            OutputFormat.ORIGINAL ->
              if (decoded.ir.alphaSemantics != AlphaSemantics.OPAQUE) ImageFormat.Png else ImageFormat.Jpeg
            is OutputFormat.Exact -> requested.format
          }
        },
      ),
    ).then { out, ctx ->
      ctx.logger.info("encoded ${out.format} -> ${out.bytes.size} bytes")
      out
    }
  }
}
```
