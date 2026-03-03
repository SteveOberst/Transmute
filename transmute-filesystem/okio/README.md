# transmute-filesystem:okio

Okio-backed implementation of `TransmuteFileSystem`.

## Overview

Wraps Square's [Okio](https://square.github.io/okio/) filesystem to implement
the `TransmuteFileSystem` interface, supporting all KMP targets that Okio
supports.

## Key Types

| Type | Purpose |
|------|---------|
| `OkioFileSystem` | `TransmuteFileSystem` implementation wrapping `okio.FileSystem` |

Internal: `OkioReadHandle`, `OkioWriteHandle`

## Usage

```kotlin
val fs: TransmuteFileSystem = OkioFileSystem(FileSystem.SYSTEM)
val data = fs.read(TPath.of("image.png"))
```

## Dependencies

- `transmute-filesystem:core`
- `okio`

## Targets

Android, Desktop JVM, iOS — via Kotlin Multiplatform.
