# transmute-model:view

Read-only and mutable views over `MediaStructure` models.

## Overview

Provides a three-tier view pattern for each format:

1. **Immutable view** — read-only access to structure fields
2. **Mutable view** — in-memory editing with `.edit {}` sugar
3. **Streaming view** — surgical channel-based writes (where applicable)

```kotlin
// Read-only inspection
val view = PngView(pngStructure)
println(view.width)

// In-place editing via .edit {} lambda
val modified = pngStructure.edit {
    ihdr = ihdr.copy(width = 100u)
}
```

## Key Types

### Abstractions

| Type | Purpose |
|------|---------|
| `StructureView<F>` | Read-only view marker interface |
| `MutableStructureView<F>` | Mutable view with `build()` method |
| `SeekableByteChannel` | Platform-agnostic seekable byte channel interface |
| `ByteArrayChannel` | In-memory `SeekableByteChannel` implementation |

### Per-Format Views

**Image:** `PngView`, `JpegView`, `BmpView`, `GifView`, `TiffView`, `WebpView`,
`HeifView`, `AvifView` — plus `MutablePngView`, `MutableJpegView`, etc.

**Audio:** `WavView`, `Mp3View`, `FlacView`, `AacView`, `M4aView`, `OpusView`,
`OggAudioView` — plus mutable counterparts.

**Video:** `Mp4View`, `MovView`, `WebmView`, `MkvView`, `AviView` — plus mutable
counterparts.

## Dependencies

- `transmute-model:core`
- `transmute-model:identify`
- `transmute-model:structure`
- `kotlinx-coroutines-core`

## Targets

Android, Desktop JVM, iOS — via Kotlin Multiplatform.
