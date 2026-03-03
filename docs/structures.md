# Structures

Structure decoding lets you parse a media file into a typed Kotlin data class that mirrors the on-disk format — PNG chunks, JPEG segments, RIFF containers, ISO-BMFF boxes, EBML elements — without decoding any pixel or sample data.

## Reading structures

```kotlin
// Auto-detect format, return null if unsupported
val structure: MediaStructure? = Transmute.inspect.structure(pngBytes.asBytes())

// Provide a format hint
val structure = Transmute.inspect.structure(pngBytes.asBytes(), ImageFormat.Png)

// Raw variant: preserves binary field representation
val rawStructure: RawMediaStructure? =
    Transmute.inspect.rawStructure(pngBytes.asBytes(), ImageFormat.Png)

// From Transmute.codec directly (throws if no decoder registered)
val structure: MediaStructure = Transmute.codec.decodeStructure(bytes, ImageFormat.Jpeg)
val raw: RawMediaStructure = Transmute.codec.decodeRawStructure(bytes, AudioFormat.Flac)
```

Check support before reading:

```kotlin
if (Transmute.codec.hasStructureDecoder(format)) {
    val structure = Transmute.codec.decodeStructure(bytes, format)
}
if (Transmute.codec.hasRawStructureDecoder(format)) {
    val raw = Transmute.codec.decodeRawStructure(bytes, format)
}
```

## Supported structures

All 21 supported formats have both a `MediaStructure` (high-level) and a `RawMediaStructure` (low-level binary fields) decoder registered by default.

### Image

| Format | Structure type | Registry key |
|--------|----------------|--------------|
| PNG    | `PngStructure` | `transmute.png` |
| JPEG   | `JpegStructure` | `transmute.jpeg` |
| BMP    | `BmpStructure` | `transmute.bmp` |
| GIF    | `GifStructure` | `transmute.gif` |
| TIFF   | `TiffStructure` | `transmute.tiff` |
| WebP   | `WebpStructure` | `transmute.webp` |
| HEIF   | `HeifStructure` | `transmute.heif` |
| AVIF   | `AvifStructure` | `transmute.avif` |

### Audio

| Format | Structure type | Registry key |
|--------|----------------|--------------|
| WAV    | `WavStructure` | `transmute.wav` |
| MP3    | `Mp3Structure` | `transmute.mp3` |
| FLAC   | `FlacStructure` | `transmute.flac` |
| AAC    | `AacStructure` | `transmute.aac` |
| M4A    | `M4aStructure` | `transmute.m4a` |
| OGG    | `OggAudioStructure` | `transmute.ogg` |
| Opus   | `OpusStructure` | `transmute.opus` |

### Video

| Format | Structure type | Registry key |
|--------|----------------|--------------|
| MP4    | `Mp4Structure` | `transmute.mp4` |
| MOV    | `MovStructure` | `transmute.mov` |
| WebM   | `WebmStructure` | `transmute.webm` |
| MKV    | `MkvStructure` | `transmute.mkv` |
| AVI    | `AviStructure` | `transmute.avi` |

## Using structure data

Cast the `MediaStructure` result to the concrete type:

```kotlin
val structure = Transmute.inspect.structure(pngBytes.asBytes(), ImageFormat.Png)
if (structure is PngStructure) {
    println("Width:  ${structure.ihdr?.width}")
    println("Height: ${structure.ihdr?.height}")
    println("Bit depth: ${structure.ihdr?.bitDepth}")
    println("Text chunks: ${structure.textChunks.size}")
}

val jpegStruct = Transmute.inspect.structure(jpegBytes.asBytes(), ImageFormat.Jpeg)
if (jpegStruct is JpegStructure) {
    println("Segments: ${jpegStruct.segments.size}")
}
```

## Editing structures (PngStructure example)

The `RawMediaStructure` types expose an `edit { }` DSL for in-place modification and round-trip re-encoding:

```kotlin
// Decode raw
val raw = Transmute.codec.decodeRawStructure(pngBytes.asBytes(), ImageFormat.Png)

// (Type-specific) convert raw → structure, edit, re-encode
// See per-format documentation for the exact API of each structure type.
```

## JSON serialization

All `MediaStructure` and `RawMediaStructure` types support kotlinx.serialization as sealed polymorphic types. They are pre-registered in `MediaStructureRegistry` with their registry keys:

```kotlin
// Serialize
val json = Json { serializersModule = MediaStructureRegistry.serializersModule }
val encoded = json.encodeToString(MediaStructure.serializer(), structure)

// Deserialize
val decoded = json.decodeFromString(MediaStructure.serializer(), encoded)
```

Plugins can register additional types:

```kotlin
// Inside TransmutePlugin.install():
scope.mediaStructures.register("myplugin.myformat", MyFormatStructure.serializer())
```
