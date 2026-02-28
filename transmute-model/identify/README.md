# transmute-model:identify

Format identification types for media file detection.

## Overview

Provides the types used during format detection: magic byte signatures,
confidence levels, container brands, and format-specific identifiers.

## Key Types

| Type | Purpose |
|------|---------|
| `MediaIdentity` | Complete identity of a detected file (format, confidence, brands, signatures) |
| `Confidence` | Detection confidence: `Low`, `Medium`, `High` |
| `Endianness` | Byte order: `Big`, `Little` |
| `MimeType` | MIME type (inline value class) |
| `FourCC` | ISO BMFF four-character code (exactly 4 chars) |
| `Brand` | ISO BMFF brand wrapper around `FourCC` |
| `RiffChunkId` | RIFF chunk identifier (4 ASCII chars) |
| `EbmlId` | EBML element identifier |
| `MagicSignature` | File magic bytes used for format detection |

## Dependencies

- `transmute-model:core`

## Targets

Android, Desktop JVM, iOS — via Kotlin Multiplatform.
