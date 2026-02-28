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

## Bulk Registration

```kotlin
// Register all built-in readers at once
DefaultStructureReaders.installDefaults()

// Or register individually
StructureReaders.register(DefaultStructureReaders.png, ImageFormat.Png)
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
