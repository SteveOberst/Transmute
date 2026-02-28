# transmute-model:stream

Types describing media streams within container formats.

## Overview

Defines codec descriptors, stream type discriminators, and per-stream information
for video, audio, and image content within container files.

## Key Types

| Type | Purpose |
|------|---------|
| `StreamInfo` | Unified stream descriptor (id, type, codec, duration, bitrate, language) |
| `VideoStreamInfo` | Width, height, frame rate, bitrate, codec, color info |
| `AudioStreamInfo` | Sample rate, channels, bits per sample, bitrate, codec |
| `ImageStreamInfo` | Width, height, bits per pixel, codec |
| `StreamType` | Extensible interface: `Video`, `Audio`, `Subtitle`, `Image`, `Data`, `Attachment` |
| `CodecDescriptor` | Name, fourCC, profile, level |
| `ColorPrimaries` | Color primaries specification |
| `TransferCharacteristics` | Transfer function specification |
| `MatrixCoefficients` | Color matrix coefficients |

## Dependencies

- `transmute-model:core`
- `transmute-model:identify`

## Targets

Android, Desktop JVM, iOS — via Kotlin Multiplatform.
