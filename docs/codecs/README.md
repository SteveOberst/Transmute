# Formats

## Platform availability

Codec support depends on the platform. Cells marked **✓** are available out of the box; **plugin** requires the GStreamer or libheif plugin.

### Image

| Format | Android | Desktop (JVM) | iOS |
|--------|---------|--------------|-----|
| JPEG   | ✓ | ✓ | ✓ |
| PNG    | ✓ | ✓ | ✓ |
| WebP   | ✓ | ✓ | ✓ |
| GIF    | ✓ (decode) | ✓ | ✓ (decode) |
| BMP    | ✓ | ✓ | ✓ |
| TIFF   | ✓ | ✓ | ✓ |
| HEIF   | ✓ | plugin (GStreamer or libheif) | ✓ |
| HEIC   | ✓ | plugin (GStreamer or libheif) | ✓ |
| AVIF   | ✓ | plugin (GStreamer or libheif) | ✓ |

### Audio

| Format | Android | Desktop (JVM) | iOS |
|--------|---------|--------------|-----|
| WAV    | ✓ | ✓ (pure-Kotlin) | ✓ |
| MP3    | ✓ | ✓ | ✓ |
| FLAC   | ✓ | ✓ (decode only) | ✓ |
| OGG    | ✓ | ✓ (decode only) | plugin (GStreamer) |
| AAC    | ✓ | plugin | ✓ |
| M4A    | ✓ | plugin | ✓ |
| Opus   | ✓ | plugin | plugin (GStreamer) |

### Video

| Format | Android | Desktop (JVM) | iOS |
|--------|---------|--------------|-----|
| MP4    | ✓ | plugin (GStreamer) | ✓ |
| MOV    | ✓ | plugin (GStreamer) | ✓ |
| WebM   | ✓ | plugin (GStreamer) | plugin (GStreamer) |
| AVI    | plugin (GStreamer) | plugin (GStreamer) | plugin (GStreamer) |
| MKV    | plugin (GStreamer) | plugin (GStreamer) | plugin (GStreamer) |

## Format objects

All format constants are `data object` singletons. Use them to build typed transmuters and compare against detected formats:

```kotlin
// Type-safe fixed-output transmuter
val transmuter: ImageTransmuter<TSource, EncodedBytes<ImageFormat.Png>> =
    Transmute.image.to(ImageFormat.Png) { scale(800, 600) }

// Compare with detected format
val format = Transmute.inspect.detectFormat(bytes)
if (format == ImageFormat.Jpeg) { /* ... */ }

// Enumerate all known formats
ImageFormat.all  // Set<ImageFormat> (excludes Unknown)
AudioFormat.all  // Set<AudioFormat>
VideoFormat.all  // Set<VideoFormat>
```

## Format properties

Every format provides:

```kotlin
format.label       // "JPEG", "MP3", "MP4", etc.
format.mimeType    // "image/jpeg", "audio/mpeg", "video/mp4", etc.
format.extension   // "jpg", "mp3", "mp4", etc.
format.containerFamily // ContainerFamily enum (Jpeg, Riff, IsoBmff, Ebml, ...)
```

## Metadata and structure support

See [inspect.md](../inspect.md) for which metadata types are extracted from each format, and [structures.md](../structures.md) for the structure types available per format.

## Format reference

| Format | MIME | Extension | Container | Details |
|--------|------|-----------|-----------|---------|
| [JPEG](jpeg.md) | `image/jpeg` | `jpg` | JFIF | |
| [PNG](png.md) | `image/png` | `png` | PNG chunks | |
| [WebP](webp.md) | `image/webp` | `webp` | RIFF | |
| [HEIF](heif.md) | `image/heif` | `heif` | ISOBMFF | |
| [HEIC](heif.md) | `image/heic` | `heic` | ISOBMFF | Shares file type constants with HEIF on Apple platforms |
| [AVIF](avif.md) | `image/avif` | `avif` | ISOBMFF | |
| [GIF](gif.md) | `image/gif` | `gif` | GIF | |
| [BMP](bmp.md) | `image/bmp` | `bmp` | DIB | Pure-Kotlin codec |
| [TIFF](tiff.md) | `image/tiff` | `tiff` | TIFF | |
| [MP3](mp3.md) | `audio/mpeg` | `mp3` | MPEG | |
| [AAC](aac.md) | `audio/aac` | `aac` | MPEG | |
| [WAV](wav.md) | `audio/wav` | `wav` | RIFF | Pure-Kotlin codec |
| [OGG](ogg.md) | `audio/ogg` | `ogg` | Ogg | |
| [FLAC](flac.md) | `audio/flac` | `flac` | FLAC | |
| [M4A](m4a.md) | `audio/mp4` | `m4a` | ISOBMFF | |
| [Opus](opus.md) | `audio/opus` | `opus` | Ogg | |
| [MP4](mp4.md) | `video/mp4` | `mp4` | ISOBMFF | |
| [WebM](webm.md) | `video/webm` | `webm` | EBML | |
| [MOV](mov.md) | `video/quicktime` | `mov` | ISOBMFF | |
| [AVI](avi.md) | `video/x-msvideo` | `avi` | RIFF | |
| [MKV](mkv.md) | `video/x-matroska` | `mkv` | EBML | |
