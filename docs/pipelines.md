# Pipelines

Every transmuter runs a three-stage pipeline:

```
[source bytes]
    │
    ▼
┌──────────────────┐
│  Decode stage    │  bytes → intermediate representation (IR)
└──────────────────┘
    │ ImageIR / AudioIR / VideoIR
    ▼
┌──────────────────┐
│ Transform stage  │  zero or more transforms applied to the IR
└──────────────────┘
    │ same IR type
    ▼
┌──────────────────┐
│  Encode stage    │  IR → EncodedBytes<OutputFormat>
└──────────────────┘
    │
    ▼
[output bytes]
```

You interact with stages via the transmuter builder DSL. All stages are optional — if you omit a block the platform default handler is used.

## Transmuter builder structure

```kotlin
Transmute.image {                  // DynamicImageTransmuterBuilder
    decode  { /* decode stage  */ }
    scale(800, 600)                // shorthand for: transform { add(ImageScaleTransform(800, 600)) }
    encode  { /* encode stage  */ }
    logger(TransmuteLogging.printLogger(LogLevel.DEBUG))
    context(myTransmuteContext)
}.transmute(source)
```

## Decode stage

```kotlin
decode {
    // Options
    options {
        // Restrict accepted input formats (skip format detection overhead)
        acceptedInputFormats += ImageFormat.Jpeg
        acceptedInputFormats += ImageFormat.Png
    }

    // Replace the entire decode pipeline with a custom handler chain
    pipeline(MyCustomDecodeHandler()) {
        then(AnotherHandler())
    }
}
```

## Transform stage

You can use the DSL shorthand extensions (recommended) or the explicit `transform { }` block:

```kotlin
// DSL shorthand (for image):
Transmute.image {
    scale(1920, 1080)
    rotate(90)
    grayscale()
}

// Equivalent explicit form:
Transmute.image {
    transform {
        add(ImageScaleTransform(1920, 1080))
        add(ImageRotateTransform(90))
        add(ImageGrayscaleTransform())
    }
}

// Insert before a specific transform type:
Transmute.video {
    transform {
        add(Transformers.video().resize(640, 480))
        before<VideoResizeTransform>(Transformers.video().trim(0, 5000))
    }
}
```

## Encode stage

```kotlin
encode {
    // Change output format
    options {
        outputFormat = OutputFormat.Exact(ImageFormat.Png) // force PNG
        // or:
        outputFormat = OutputFormat.Original               // keep input format
        metadataPolicy = MetadataPolicy.STRIP              // or PRESERVE (default)
    }

    // Provide format-specific options directly
    options(JpegEncodeOptions(quality = 0.85f))

    // Replace the entire encode pipeline
    pipeline(MyEncoder()) {
        then(PostProcessHandler())
    }
}
```

### `OutputFormat`

| Value | Behaviour |
|-------|-----------|
| `OutputFormat.Original` | Re-encode to the same format that was decoded (default) |
| `OutputFormat.Exact(format)` | Always encode to the given format |

### Fixed-output transmuters

`Transmute.image.to(ImageFormat.Png) { ... }` returns a `ImageTransmuter<TSource, EncodedBytes<ImageFormat.Png>>`.
The output format is encoded in the return type, so post-processing handlers can be type-safe:

```kotlin
val transmuter: ImageTransmuter<TSource, EncodedBytes<ImageFormat.Png>> =
    Transmute.image.to(ImageFormat.Png) {
        scale(800, 600)
    }
val result: EncodedBytes<ImageFormat.Png> = transmuter.transmute(source)
val format: ImageFormat.Png = result.format  // compile-time guarantee
```

## Custom input/output types

Use `Transmute.image.custom` for non-standard input or output types:

```kotlin
// Custom output type (e.g. your own wrapper)
val transmuter: ImageTransmuter<TSource, MyOutput> = Transmute.image.custom.out {
    encode {
        pipeline(MyOutputEncoder())
    }
}

// Custom input type
val transmuter: ImageTransmuter<MyInput, EncodedBytes<ImageFormat>> = Transmute.image.custom.from {
    decode {
        pipeline(MyInputDecoder())
    }
}

// Both custom
val transmuter = Transmute.image.custom.fromOut<MyInput, MyOutput> {
    decode { pipeline(MyInputDecoder()) }
    encode { pipeline(MyOutputEncoder()) }
}

// Fixed output + custom input
val transmuter = Transmute.image.custom.toFrom(ImageFormat.Webp) {
    decode { pipeline(MyInputDecoder()) }
}
```

## Per-operation context

```kotlin
val ctx = TransmuteContext {
    logger = TransmuteLogging.printLogger(LogLevel.DEBUG)
}

Transmute.image {
    context(ctx)
    scale(800, 600)
}.transmute(source)
```

## Reusing transmuters

Transmuters are immutable and thread-safe once built. Build once, call `transmute()` many times:

```kotlin
val scaler = Transmute.image {
    scale(maxWidth = 2048, maxHeight = 2048)
    encode { options { outputFormat = OutputFormat.Exact(ImageFormat.Jpeg) } }
}

val results = images.map { scaler.transmute(it) }
```

## `wouldTransmute`

Check whether a transmuter would actually apply any transforms to a given source (skip expensive I/O when unnecessary):

```kotlin
val transmuter = Transmute.image { scale(800, 600) }
if (transmuter.wouldTransmute(ImageHint(width = 400, height = 300))) {
    // image is smaller than target — scale would be a no-op
}
```

## Transmuter types summary

| Builder | Domain | Output type |
|---------|--------|-------------|
| `Transmute.image { }` | Image | `EncodedBytes<ImageFormat>` |
| `Transmute.image.to(F) { }` | Image | `EncodedBytes<F>` (type-safe) |
| `Transmute.audio { }` | Audio | `EncodedBytes<AudioFormat>` |
| `Transmute.audio.to(F) { }` | Audio | `EncodedBytes<F>` (type-safe) |
| `Transmute.video { }` | Video | `EncodedBytes<VideoFormat>` |
| `Transmute.video.to(F) { }` | Video | `EncodedBytes<F>` (type-safe) |
| `Transmute.image.custom.out { }` | Image | Custom `OUT` |
| `Transmute.image.custom.from { }` | Image | `EncodedBytes<ImageFormat>`, custom `IN` |
| `Transmute.image.custom.fromOut { }` | Image | Custom `IN` and `OUT` |
| `Transmute.image.custom.toFrom(F) { }` | Image | `EncodedBytes<F>`, custom `IN` |
