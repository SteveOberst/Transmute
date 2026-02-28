# transmute-model:metadata

Structured metadata types for media file tags.

## Overview

Provides types for representing tag data found in media files — keys, values,
sources, and bundles aggregating metadata from multiple schemas (ID3, Vorbis
comments, EXIF, XMP, etc.).

## Key Types

| Type | Purpose |
|------|---------|
| `MetadataBundle` | Aggregates all metadata sets from a file |
| `MetadataSet` | Group of entries from a single source/schema |
| `MetadataEntry` | Single raw key-value entry |
| `MetadataField` | Enriched entry with source provenance |
| `MetadataKey` | Well-known metadata key types |
| `MetadataValue` | Typed metadata values |
| `MetadataKind` | Kind discriminator |
| `MetadataFlag` | Flags: `Estimated`, `Truncated`, `Deprecated`, `Ambiguous` |
| `MetadataSource` | Provenance of metadata (which schema/container it came from) |

## Dependencies

- `transmute-model:core`
- `transmute-model:identify`

## Targets

Android, Desktop JVM, iOS — via Kotlin Multiplatform.
