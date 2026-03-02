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
transmute.inspect // -> format detection & lightweight probing
transmute.structure // -> structure read / write / transform
```

## Key Types

| Type | Purpose |
|------|---------|
| `Transmute` | Main facade class — builder DSL, plugin installation, domain access |
| `TransmuteCodec` | Low-level codec facade (decode, encode, format detection) |
| `TransmuteInspect` | Format detection and lightweight probing |
| `TransmuteStructure` | Structure read / write / in-place transforms via `TChannel` |
| `TransmuteImage` | Image transcoding builder (`from → to`) |
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

## Lambda Sugar & Coroutine Support

Read with a trailing lambda — the parsed structure is the receiver:

```kotlin
val width: Int = transmute.structure.read<Png>(src, ImageFormat.Png) {
    ihdr.width.toInt()
}
```

In-place transform via `TChannel`:

```kotlin
val ch: TChannel = fs.channel(TPath.of("image.png"))
transmute.structure.transform<Png>(ch, ImageFormat.Png) {
    edit { ihdr = ihdr.copy(width = 100u) }
}
```

All I/O-bound methods are `suspend` functions — no blocking overloads. If callers
need synchronous execution they can use `runBlocking {}`.

## Targets

Android, Desktop JVM, iOS (when building on Mac) — via Kotlin Multiplatform.

## Dependencies

- `transmute-codec` — pipeline & codec abstractions
- `transmute-audio` / `transmute-image` / `transmute-video` — domain modules
- `transmute-model:structure` — typed file structures
- `transmute-structure` — concrete structure readers
- `kotlinx-coroutines-core`
