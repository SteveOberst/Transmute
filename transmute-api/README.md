# transmute-api

The public API facade — the primary entry point for using the Transmute library.

## Overview

`transmute-api` provides the `Transmute` class, which serves as the central hub
for all media operations: decoding, encoding, format detection, structure parsing,
and in-place transforms. Everything is reachable through its domain-specific
properties.

```kotlin
val transmute = Transmute {
    plugins {
        install(GStreamer) // optional plugin
    }
}

// Domain access
transmute.image   // -> image transcoding
transmute.audio   // -> audio transcoding
transmute.video   // -> video transcoding
transmute.codec   // -> low-level decode / encode / detect
transmute.inspect // -> format detection, lightweight probing, structure parsing
```

## Key Types

| Type | Purpose |
|------|---------|
| `Transmute` | Main facade class — builder DSL, plugin installation, domain access |
| `TransmuteCodec` | Low-level codec facade (decode, encode, format detection) |
| `TransmuteInspect` | Format detection, lightweight probing, structure and metadata parsing |
| `TransmuteImage` | Image transcoding builder (`from -> to`) |
| `TransmuteAudio` | Audio transcoding builder |
| `TransmuteVideo` | Video transcoding builder |
| `Transmuter<IN, OUT>` | Immutable, reusable transmutation executor |
| `Transformers` | Static factory for domain-specific transforms |
| `TransmutePlugin<C>` | Plugin extension point interface |
| `TransmuteScope` | Mutable registries exposed to plugins during installation |

## I/O Abstractions

Suspending byte-stream types for non-blocking I/O on every platform:

| Type | Purpose |
|------|---------|
| `TSource` | Read-only sequential byte source (`suspend fun readAll()`) |
| `TSink` | Write-only sequential byte sink (`suspend fun writeAll()`) |
| `TChannel` | Combined read + write channel for in-place transforms |

In-memory implementations (`ByteArraySource`, `ByteArraySink`,
`ByteArrayChannel`) are provided for testing. Bridge extensions
`Bytes.asSource()` and `Bytes.asChannel()` create these from a `Bytes` value.

## Coroutine Support

All I/O-bound methods are `suspend` functions — no blocking overloads. If callers
need synchronous execution they can use `runBlocking {}`.

```kotlin
// Detect format
val format = Transmute.inspect.detectFormat(bytes)

// Parse typed structure
val structure = Transmute.inspect.structure(bytes)  // -> MediaStructure?

// Full inspection (format + structure + metadata)
val inspection = Transmute.inspect.inspect(bytes)   // -> MediaInspection
```

## Targets

Android, Desktop JVM, iOS (when building on Mac) — via Kotlin Multiplatform.

## Dependencies

- `transmute-codec` — pipeline & codec abstractions
- `transmute-audio` / `transmute-image` / `transmute-video` — domain modules
- `transmute-model:structure` — typed file structures
- `transmute-structure` — concrete structure readers
- `kotlinx-coroutines-core`
