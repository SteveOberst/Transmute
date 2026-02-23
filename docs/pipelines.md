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
data class NamedBytes(val name: String, val bytes: ByteArray)

class NamedBytesToBytesHandler : PipelineHandler<NamedBytes, Bytes> {
  override suspend fun handle(value: NamedBytes, context: TransmuteContext): Bytes =
    value.bytes.asBytes()
}

val t = Transmute.imageFrom<NamedBytes> {
  decode {
    options { acceptedInputFormats += setOf(ImageFormat.Png, ImageFormat.Jpeg) }

    pipeline(start = NamedBytesToBytesHandler() + ImageCodecs.Decode.DEFAULT)
  }
}
```

## Dynamic Encode Format Selection

Output format selection is an **encode concern** (via `encode { options(...) }` or a handler policy),
not a builder-level knob.

```kotlin
val t = Transmute.image {
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
