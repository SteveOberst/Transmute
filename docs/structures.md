# Structure Reading & Writing

`Transmute.structure` parses raw file bytes into typed Kotlin data classes that mirror the on-disk binary layout — without decoding pixel or sample data.

This is useful for:

- Inspecting container metadata (chunk sizes, header fields, stream layouts)
- Rewriting/patching files structurally (e.g. strip metadata, reorder atoms)
- Round-tripping: `read → modify → write` without re-encoding
- Building diagnostic or analysis tools

## Quick Start

```kotlin
// Auto-detect format and read structure
val structure = Transmute.structure.read(fileBytes.asBytes())

// Format-explicit read (returns a typed structure)
val png: Png = Transmute.structure.read(pngBytes.asBytes(), ImageFormat.Png)
val wav: Wav = Transmute.structure.read(wavBytes.asBytes(), AudioFormat.Wav)

// Write a structure back to bytes
val roundTripped: Bytes = Transmute.structure.write(png)
```

## How It Works

Structure reading sits alongside codec decode/encode but operates at a lower level:

| Layer         | Input     | Output                                    | Pixel/sample data               |
|---------------|-----------|-------------------------------------------|---------------------------------|
| **Structure** | Raw bytes | Typed data class (mirrors on-disk layout) | Preserved as opaque `ByteArray` |
| **Codec**     | Raw bytes | IR (`ImageIR` / `AudioIR` / `VideoIR`)    | Fully decoded                   |

A `StructureReader<S>` reads bytes into a `MediaStructure` subtype. Every `MediaStructure` implements `toBytes()` for lossless round-tripping.

## Supported Formats

### Image

| Format | Structure Type | Container     |
|--------|----------------|---------------|
| PNG    | `Png`          | Chunk-based   |
| JPEG   | `Jpeg`         | Segment-based |
| BMP    | `Bmp`          | Fixed header  |
| GIF    | `Gif`          | Block-based   |
| TIFF   | `Tiff`         | IFD-based     |
| WebP   | `Webp`         | RIFF          |
| HEIF   | `Heif`         | ISO BMFF      |
| AVIF   | `Avif`         | ISO BMFF      |

### Audio

| Format     | Structure Type | Container         |
|------------|----------------|-------------------|
| WAV        | `Wav`          | RIFF              |
| MP3        | `Mp3`          | ID3 + MPEG frames |
| FLAC       | `Flac`         | Metadata blocks   |
| AAC        | `Aac`          | ADTS              |
| M4A        | `M4a`          | ISO BMFF          |
| OGG Vorbis | `OggAudio`     | Ogg               |
| Opus       | `Opus`         | Ogg               |

### Video

| Format | Structure Type | Container |
|--------|----------------|-----------|
| MP4    | `Mp4`          | ISO BMFF  |
| MOV    | `Mov`          | ISO BMFF  |
| WebM   | `Webm`         | EBML      |
| MKV    | `Mkv`          | EBML      |
| AVI    | `Avi`          | RIFF      |

All structure readers live in the `transmute-structure` module.
You can also register custom readers (see below).

## Auto-Detection

When you call `Transmute.structure.read(bytes)` without specifying a format:

1. The codec-level format detector (`Transmute.inspect.detectFormat(...)`) runs first for a precise lookup.
2. If that fails, each registered reader's `canRead()` method is tried as a fallback (magic-byte sniffing).

## Writing to a Sink

For streaming or I/O destinations, use a `TSink` or `StructureSink`:

```kotlin
// Write to a TSink (suspending, non-blocking)
val sink: TSink = fs.sink(TPath.of("output.png"))
transmute.structure.writeTo(pngStructure, sink)
sink.close()

// In-memory sink
val memSink = ByteArraySink()
transmute.structure.writeTo(pngStructure, memSink)
val raw: ByteArray = memSink.collect()
```

## Reading from a TSource

Read directly from a `TSource` — all bytes are consumed and parsed:

```kotlin
val src: TSource = fs.source(TPath.of("image.png"))
val png: Png = transmute.structure.read<Png>(src, ImageFormat.Png)
src.close()

// Auto-detect format
val src2: TSource = fs.source(TPath.of("unknown-file"))
val structure: MediaStructure = transmute.structure.read(src2)
```

## Lambda Sugar

Pass a trailing lambda to `read` — the parsed structure is the receiver:

```kotlin
val width: Int = transmute.structure.read<Png>(src, ImageFormat.Png) {
    ihdr.width.toInt()
}
```

## In-Place Transform via TChannel

Read a file, mutate the structure, and write it back through the same channel:

```kotlin
val ch: TChannel = fs.channel(TPath.of("image.png"))
transmute.structure.transform<Png>(ch, ImageFormat.Png) {
    edit { ihdr = ihdr.copy(width = 100u) }
}
ch.close()
```

The `transform` lambda receives the parsed structure as its receiver and must
return the modified copy. Use `.edit {}` inside the lambda to access the format's
mutable view.

## I/O Abstractions

All I/O-bound structure methods are `suspend` functions — no blocking overloads.
If callers need synchronous execution they can use `runBlocking {}`.

| Type       | Purpose                                               |
|------------|-------------------------------------------------------|
| `TSource`  | Read-only sequential byte source                      |
| `TSink`    | Write-only sequential byte sink                       |
| `TChannel` | Combined read + write channel for in-place transforms |

In-memory implementations (`ByteArraySource`, `ByteArraySink`,
`ByteArrayChannel`) are provided for testing. Bridge extensions
`Bytes.asSource()` and `Bytes.asChannel()` create these directly from a
`Bytes` value. All types live in `dev.transmute.io`.

## Reading Specific Structure Fields

Each structure type is a Kotlin data class whose fields match the on-disk layout. Extension properties on the companion provide convenient typed access to common fields:

```kotlin
val png: Png = Transmute.structure.read(pngBytes.asBytes(), ImageFormat.Png)

// Navigate the chunk tree
val ihdr = png.chunks.first() // PngChunk with type = "IHDR"
```

```kotlin
val wav: Wav = Transmute.structure.read(wavBytes.asBytes(), AudioFormat.Wav)

// RIFF container children
val fmtChunk = wav.riffHeader.children.firstOrNull { it.chunkId == "fmt " }
```

```kotlin
val jpeg: Jpeg = Transmute.structure.read(jpegBytes.asBytes(), ImageFormat.Jpeg)

// JPEG segments (SOI, APP0, DQT, SOF, SOS, EOI, ...)
val segments = jpeg.segments
```

## Custom Structure Readers

Register a custom reader for a format that Transmute does not yet support:

```kotlin
class MyCustomReader : StructureReader<MyCustomStructure> {
    override fun canRead(source: Bytes): Boolean {
        if (source.data.size < 4) return false
        val h = source.data
        return h[0] == 0x4D.toByte() && h[1] == 0x59.toByte() // "MY" magic
    }

    override fun read(source: Bytes): MyCustomStructure { /* ... */ }
}

// Register for a specific format
Transmute.structure.register(MyCustomReader(), MyFormat)
```

Custom readers override built-in defaults when registered for the same format.

## Relationship to Views

The `transmute-model:view` module provides `StructureView` classes that offer a higher-level, domain-aware API on top of raw structures. Views delegate to structure extension properties for convenient access to width, height, sample rate, and other semantic fields — without re-parsing.

| Layer                                    | Purpose                                                    |
|------------------------------------------|------------------------------------------------------------|
| `MediaStructure`                         | Immutable data class mirroring the on-disk binary layout   |
| Extension properties                     | Typed accessors computed from raw structure fields         |
| `StructureView` / `MutableStructureView` | Higher-level read/write API backed by extension properties |
