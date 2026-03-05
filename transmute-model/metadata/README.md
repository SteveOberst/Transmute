# transmute-model:metadata

Structured metadata types for media file tags.

## Overview

Provides `@Serializable` data classes for every metadata schema that Transmute
can extract from media files. Each type implements the `MediaMetadata` marker
interface and is registered with `MediaMetadataRegistry` for polymorphic
serialisation via `TypedEnvelopeSerializer`.

## Metadata Types

| Type | Registry ID | Schema |
|------|-------------|--------|
| `ExifMetadata` | `transmute.exif` | EXIF (TIFF-based image tags) |
| `XmpMetadata` | `transmute.xmp` | XMP (XML-based Adobe metadata) |
| `IccProfileMetadata` | `transmute.icc` | ICC colour profiles |
| `Id3v1Metadata` | `transmute.id3v1` | ID3v1 (MP3 tail tag) |
| `Id3v2Metadata` | `transmute.id3v2` | ID3v2 (MP3/AAC header tag) |
| `PngTextMetadata` | `transmute.png-text` | PNG tEXt / iTXt / zTXt chunks |
| `VorbisCommentMetadata` | `transmute.vorbis-comment` | Vorbis comments (OGG/FLAC) |
| `RiffInfoMetadata` | `transmute.riff-info` | RIFF INFO LIST (WAV/AVI) |
| `ItunesMetadata` | `transmute.itunes` | iTunes/MP4 ilst atoms |
| `MatroskaTagsMetadata` | `transmute.matroska-tags` | Matroska/WebM tags |

### Common patterns

Several types use a **typed-slots + extra + order** pattern for well-known
fields (e.g. `ExifMetadata`, `ItunesMetadata`, `RiffInfoMetadata`,
`VorbisCommentMetadata`, `Id3v2Metadata`). Known fields have dedicated
properties; unrecognised entries go into `extra`; an `order` list preserves
the original sequence for round-trip fidelity.

### Shared helpers

| Type | Purpose |
|------|---------|
| `PayloadRef` | Opaque reference to a byte range inside a container (offset + length) |
| `ByteSlice` | Lightweight view over a `Bytes` buffer |

## Dependencies

- `transmute-model:core`
- `transmute-model:identify`

## Targets

Android, Desktop JVM, iOS — via Kotlin Multiplatform.
