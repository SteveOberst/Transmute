# transmute-structure

Concrete `StructureReader` implementations for all supported media formats.

## Overview

Parses raw file bytes into the typed `MediaStructure` models defined in
`transmute-model:structure`. Ships readers for 20 formats across image, audio,
and video domains.

## Readers

### Image

`PngStructureReader`, `JpegStructureReader`, `BmpStructureReader`,
`GifStructureReader`, `TiffStructureReader`, `WebpStructureReader`,
`HeifStructureReader`, `AvifStructureReader`

### Audio

`WavStructureReader`, `Mp3StructureReader`, `FlacStructureReader`,
`AacStructureReader`, `M4aStructureReader`, `OggAudioStructureReader`,
`OpusStructureReader`

### Video

`Mp4StructureReader`, `MovStructureReader`, `WebmStructureReader`,
`MkvStructureReader`, `AviStructureReader`

## Pre-built Instances

`DefaultStructureReaders` ships singleton instances for all 20 readers.
`DefaultStructureDecoders` ships pre-built `Decoder` wrappers (raw + typed)
for every format.

Register inside a `TransmutePlugin`:

```kotlin
// Register one reader
scope.codecs.image.structureDecoders.register(
    ImageFormat.Png, DefaultStructureDecoders.png
)

// Bulk-register all image structure decoders
DefaultStructureDecoders.allImageDecoders.forEach { dec ->
    dec.decodableFormats.forEach { fmt ->
        scope.codecs.image.structureDecoders.register(fmt as ImageFormat, dec)
    }
}

// Access the full reader list (recommended priority order)
val readers = DefaultStructureReaders.all
```

### Common Parsers

`ContainerParsers` provides shared parsing utilities for ISO BMFF, RIFF, EBML,
and Ogg container formats.

## Dependencies

- `transmute-model:core`
- `transmute-model:identify`
- `transmute-model:structure`

## Targets

Android, Desktop JVM, iOS — via Kotlin Multiplatform.
