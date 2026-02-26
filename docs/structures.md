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

| Layer | Input | Output | Pixel/sample data |
|-------|-------|--------|-------------------|
| **Structure** | Raw bytes | Typed data class (mirrors on-disk layout) | Preserved as opaque `ByteArray` |
| **Codec** | Raw bytes | IR (`ImageIR` / `AudioIR` / `VideoIR`) | Fully decoded |

A `StructureReader<S>` reads bytes into a `MediaStructure` subtype. Every `MediaStructure` implements `toBytes()` for lossless round-tripping.

## Supported Formats

| Format | Module | Structure Type |
|--------|--------|----------------|
| PNG | `transmute-structure` | `Png` |
| JPEG | `transmute-structure` | `Jpeg` |
| BMP | `transmute-structure` | `Bmp` |
| WAV | `transmute-structure` | `Wav` |
| MP3 | `transmute-structure` | `Mp3` |
| FLAC | `transmute-structure` | `Flac` |

More formats will be added over time. You can also register custom readers (see below).

## Auto-Detection

When you call `Transmute.structure.read(bytes)` without specifying a format:

1. The codec-level format detector (`Transmute.inspect.detectFormat(...)`) runs first for a precise lookup.
2. If that fails, each registered reader's `canRead()` method is tried as a fallback (magic-byte sniffing).

## Writing to a Sink

For streaming or I/O destinations, use a `StructureSink`:

```kotlin
// In-memory sink
val sink = BytesSink()
Transmute.structure.writeTo(pngStructure, sink)
val raw: Bytes = sink.collect()
```

`StructureSink` is a simple interface you can implement for file I/O, network streams, or any other destination:

```kotlin
interface StructureSink {
    suspend fun write(structure: MediaStructure)
    suspend fun flush() {}
    suspend fun close() {}
}
```

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
class TiffStructureReader : StructureReader<MyTiffStructure> {
    override fun canRead(source: Bytes): Boolean {
        if (source.data.size < 4) return false
        val h = source.data
        // "II" (little-endian) or "MM" (big-endian) + magic 42
        return (h[0] == 0x49.toByte() && h[1] == 0x49.toByte() && h[2] == 0x2A.toByte() && h[3] == 0x00.toByte()) ||
               (h[0] == 0x4D.toByte() && h[1] == 0x4D.toByte() && h[2] == 0x00.toByte() && h[3] == 0x2A.toByte())
    }

    override fun read(source: Bytes): MyTiffStructure { /* ... */ }
}

// Register for a specific format
Transmute.structure.register(TiffStructureReader(), ImageFormat.Tiff)
```

Custom readers override built-in defaults when registered for the same format.

## Relationship to Views

The `transmute-model:view` module provides `StructureView` classes that offer a higher-level, domain-aware API on top of raw structures. Views delegate to structure extension properties for convenient access to width, height, sample rate, and other semantic fields — without re-parsing.

| Layer | Purpose |
|-------|---------|
| `MediaStructure` | Immutable data class mirroring the on-disk binary layout |
| Extension properties | Typed accessors computed from raw structure fields |
| `StructureView` / `MutableStructureView` | Higher-level read/write API backed by extension properties |
