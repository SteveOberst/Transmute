# transmute-model:structure

Typed data models for every supported media format's binary layout.

## Overview

Each supported format is represented as a Kotlin `data class` that mirrors its
on-disk structure (headers, chunks, atoms, boxes). These models can be
round-tripped: read from raw bytes and written back to produce valid files.

## Key Types

### Core Abstractions

| Type | Purpose |
|------|---------|
| `MediaStructure` | Marker interface — every format model implements this + `BinarySerializable` |
| `StructureReader<S>` | Interface to parse `Bytes` → `S : MediaStructure` |
| `StructureReaders` | Global registry of readers, with `read()` and `readAuto()` |
| `StructureSink` / `BytesSink` | Output abstractions for writing structures |
| `StructureReadException` | Thrown on parse failure |

### Image Structures

`Png`, `Jpeg`, `Bmp`, `Gif`, `Tiff`, `Webp`, `Heif`, `Avif`

### Audio Structures

`Wav`, `Mp3`, `Flac`, `Aac`, `M4a`, `OggAudio`, `Opus`

### Video Structures

`Mp4`, `Mov`, `Webm`, `Mkv`, `Avi`

### Common Containers

`IsoBmffBoxRef`, `IsoBmffFtyp` (ISO BMFF), `RiffChunkRef` (RIFF),
`EbmlElementRef` (EBML/Matroska), `OggTypes` (Ogg)

## Dependencies

- `transmute-model:core`
- `transmute-model:identify`

## Targets

Android, Desktop JVM, iOS — via Kotlin Multiplatform.
