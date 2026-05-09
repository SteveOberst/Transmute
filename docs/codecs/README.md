# Formats

## Platform availability

Codec support depends on the platform. Each cell states whether the format can be decoded, encoded, or both on that target, and calls out plugin requirements when they exist.

### Image

| Format | Android | Desktop (JVM) | iOS |
|---|---|---|---|
| JPEG   | decode + encode | decode + encode | decode + encode |
| PNG    | decode + encode | decode + encode | decode + encode |
| WebP   | decode + encode | decode + encode | decode + encode |
| GIF    | decode only | decode + encode | decode only |
| BMP    | decode + encode | decode + encode | decode + encode |
| TIFF   | decode + encode | decode + encode | decode + encode |
| HEIF   | decode only | decode + encode; plugin: libheif | decode + encode |
| HEIC   | decode only | decode + encode; plugin: libheif | decode + encode |
| AVIF   | decode only | decode + encode; plugin: libheif | decode + encode |

### Audio

| Format | Android | Desktop (JVM) | iOS |
|---|---|---|---|
| WAV    | decode + encode | decode + encode (pure Kotlin) | decode + encode |
| MP3    | decode + encode | decode + encode | decode + encode |
| FLAC   | decode + encode | decode only built-in; encode requires plugin: GStreamer | decode + encode |
| OGG    | decode + encode | decode only built-in; encode requires plugin: GStreamer | decode + encode; plugin: GStreamer |
| AAC    | decode + encode | decode + encode; plugin: GStreamer | decode + encode |
| M4A    | decode + encode | decode + encode; plugin: GStreamer | decode + encode |
| Opus   | decode built-in; encode hardware dependent | decode + encode; plugin: GStreamer | decode + encode; plugin: GStreamer |

### Video

| Format | Android | Desktop (JVM) | iOS |
|---|---|---|---|
| MP4    | decode + encode | decode + encode; plugin: GStreamer | decode + encode |
| MOV    | decode + encode | decode + encode; plugin: GStreamer | decode + encode |
| WebM   | decode + encode | decode + encode; plugin: GStreamer | decode + encode; plugin: GStreamer |
| AVI    | decode + encode; plugin: GStreamer | decode + encode; plugin: GStreamer | decode + encode; plugin: GStreamer |
| MKV    | decode + encode; plugin: GStreamer | decode + encode; plugin: GStreamer | decode + encode; plugin: GStreamer |

## Format objects

All format constants are `data object` singletons. Use them to build typed transmuters and compare against detected formats:

```kotlin
// Type-safe fixed-output transmuter
val transmuter: ImageTransmuter<TSource, EncodedBytes<ImageFormat.Png>> =
    transmute().image.to(ImageFormat.Png) { scale(800, 600) }

// Compare with detected format
val format = transmute().inspect.detectFormat(bytes)
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
|---|---|---|---|---|
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



