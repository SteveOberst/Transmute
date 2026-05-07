# transmute-model:core

Foundation types for the entire Transmute library.

## Overview

Every other module depends on `transmute-model:core`. It defines the canonical
abstractions for binary data, media formats, encode/decode options, serialization
primitives, and domain-specific typed wrappers.

## Key Types

### Binary Data

| Type | Purpose |
|---|---|
| `Bytes` | `@JvmInline value class` wrapping `ByteArray` - canonical binary container |
| `BinarySerializable` | Interface for types that serialize to on-disk binary format |
| `BinarySerializer` | Custom `KSerializer` for binary round-tripping |
| `ByteRange` / `BoundedBytes` | Byte range and bounded byte types |

### Format Descriptors

| Type | Purpose |
|---|---|
| `MediaFormat<D, E>` | Typed format descriptor (label, mimeType, extension, containerFamily) |
| `ContainerFamily` | Extensible grouping: `IsoBmff`, `Riff`, `Ebml`, `Ogg`, `Mpeg`, etc. |
| `UnknownFormat` | Domain-agnostic unknown format sentinel |

### Options

| Type | Purpose |
|---|---|
| `DecodeOptions` / `EncodeOptions` | Marker interfaces for codec configuration |
| `NoDecodeOptions` / `NoEncodeOptions` | Default no-op options |

### Typed Primitives

Inline value classes for compile-time unit safety:

`Utf8String`, `AsciiString`, `Latin1String`, `UriString`, `Iso8601String`,
`LanguageTag`, `Pixels`, `Hertz`, `Channels`, `Bitrate`, `DurationMicros`,
`BitsPerSample`, `Rational`, `ByteLength`, `StreamId`

### Extension Points

| Type | Purpose |
|---|---|
| `ModelExtension` | Extension point for custom metadata in model objects |

## Dependencies

- `kotlinx-serialization-core`

## Targets

Android, Desktop JVM, iOS - via Kotlin Multiplatform.
