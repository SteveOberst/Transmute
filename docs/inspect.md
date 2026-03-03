# Inspect

`Transmute.inspect` provides lightweight probing and inspection operations that do not require building a full transmuter. It sits on top of `Transmute.codec` and adds higher-level helpers.

## API overview

```
TransmuteInspect
├── detectFormat(bytes / source / ByteArray): MediaFormat<*,*>
├── metadata(bytes / source / ByteArray, format?): List<MediaMetadata>
├── structure(bytes, format?): MediaStructure?
├── rawStructure(bytes, format?): RawMediaStructure?
├── inspect(bytes / source / ByteArray, options?): MediaInspection
│
├── image: InspectImage
│   └── detectFormat(bytes / source / ByteArray): ImageFormat
│
├── audio: InspectAudio
│   └── detectFormat(bytes / source / ByteArray): AudioFormat
│
└── video: InspectVideo
    ├── detectFormat(bytes / source / ByteArray): VideoFormat
    └── thumbnailFirstFrame(source, imageEncodeOptions?, decodeRange?): EncodedBytes<ImageFormat>
```

## Format detection

```kotlin
val format: MediaFormat<*, *> = Transmute.inspect.detectFormat(bytes)
when (format) {
    is ImageFormat -> println("Image: ${format.label}")
    is AudioFormat -> println("Audio: ${format.label}")
    is VideoFormat -> println("Video: ${format.label}")
    UnknownFormat  -> println("Unknown format")
}

// Domain-specific variant (stricter — only considers image bytes)
val imageFormat: ImageFormat = Transmute.inspect.image.detectFormat(bytes)
```

See [format-detection.md](format-detection.md) for details on how detection works.

## Metadata extraction

`metadata()` decodes all embedded metadata blocks for a file. Returns an empty list if no metadata decoder is registered for the detected format.

```kotlin
// Auto-detect format, then extract metadata
val metadata: List<MediaMetadata> = Transmute.inspect.metadata(bytes)

// Provide format hint to skip detection
val metadata = Transmute.inspect.metadata(bytes, ImageFormat.Jpeg)

// From a TSource (reads once)
val metadata = Transmute.inspect.metadata(source)
```

### Working with metadata entries

Each entry is a `MediaMetadata` subtype. Cast with `when` or use `filterIsInstance`:

```kotlin
val exif = metadata.filterIsInstance<ExifMetadata>().firstOrNull()
val xmp  = metadata.filterIsInstance<XmpMetadata>().firstOrNull()
val icc  = metadata.filterIsInstance<IccProfileMetadata>().firstOrNull()

// Audio
val id3v2      = metadata.filterIsInstance<Id3v2Metadata>().firstOrNull()
val vorbis     = metadata.filterIsInstance<VorbisCommentMetadata>().firstOrNull()
val itunes     = metadata.filterIsInstance<ItunesMetadata>().firstOrNull()
val riffInfo   = metadata.filterIsInstance<RiffInfoMetadata>().firstOrNull()

// Video
val matroska   = metadata.filterIsInstance<MatroskaTagMetadata>().firstOrNull()
```

### Supported metadata by format

| Format | Metadata types |
|--------|---------------|
| JPEG   | ExifMetadata, XmpMetadata, IccProfileMetadata |
| TIFF   | ExifMetadata, XmpMetadata, IccProfileMetadata |
| PNG    | PngTextMetadata, XmpMetadata |
| WebP   | ExifMetadata, XmpMetadata, IccProfileMetadata |
| HEIF   | ExifMetadata, XmpMetadata |
| AVIF   | ExifMetadata, XmpMetadata |
| MP3    | Id3v1Metadata, Id3v2Metadata |
| FLAC   | VorbisCommentMetadata |
| OGG    | VorbisCommentMetadata |
| Opus   | VorbisCommentMetadata |
| WAV    | RiffInfoMetadata |
| M4A    | ItunesMetadata |
| AAC    | ItunesMetadata |
| MP4    | ItunesMetadata |
| MOV    | ItunesMetadata |
| AVI    | RiffInfoMetadata |
| WebM   | MatroskaTagMetadata |
| MKV    | MatroskaTagMetadata |

## Structure decoding

`structure()` parses the file into a typed `MediaStructure` Kotlin data class that mirrors the on-disk layout. Useful for reading headers, dimensions, bitrates, sample rates, etc. without a full transcode.

```kotlin
// Auto-detect format
val structure: MediaStructure? = Transmute.inspect.structure(bytes)

// Provide format hint
val pngStructure = Transmute.inspect.structure(pngBytes.asBytes(), ImageFormat.Png)

// Raw (lower-level): preserves the binary field representation
val rawPng: RawMediaStructure? = Transmute.inspect.rawStructure(pngBytes.asBytes(), ImageFormat.Png)
```

`structure()` returns `null` if no structure decoder is registered for the format. Use `Transmute.codec.hasStructureDecoder(format)` to check first.

See [structures.md](structures.md) for the full structure type reference.

## High-level inspection

`inspect()` combines format detection, structure decoding, and metadata extraction in a single call:

```kotlin
val inspection: MediaInspection = Transmute.inspect.inspect(bytes)

println("Domain:    ${inspection.domain}")         // MediaDomain.IMAGE / AUDIO / VIDEO / NONE
println("Format:    ${inspection.format.label}")   // "JPEG", "MP3", etc.
println("Size:      ${inspection.sizeBytes} bytes")
println("Structure: ${inspection.structure}")
println("Metadata:  ${inspection.metadata.size} blocks")

// From a TSource (reads once, efficient)
val inspection = Transmute.inspect.inspect(source)
```

### InspectOptions

Control what is included in the result:

```kotlin
val inspection = Transmute.inspect.inspect(
    bytes,
    InspectOptions(
        includeStructure    = true,   // default: true
        includeRawStructure = false,  // default: false
        includeMetadata     = true,   // default: true
    )
)
```

## Video thumbnail

Extract a PNG or other image from the first decodable video frame without decoding the full file:

```kotlin
val thumbnail: EncodedBytes<ImageFormat> =
    Transmute.inspect.video.thumbnailFirstFrame(source)

// Custom encode options and decode range
val thumbnail = Transmute.inspect.video.thumbnailFirstFrame(
    source,
    imageEncodeOptions = JpegEncodeOptions(quality = 0.9f),
    decodeRange = TimeRangeMs(startMs = 0, endMsExclusive = 5_000),
)
val jpegBytes: ByteArray = thumbnail.bytes.data
```

> **Note:** `thumbnailFirstFrame` requires a video codec capable of decoding the file. On Desktop, install the GStreamer plugin for MP4/WebM/MKV support.
