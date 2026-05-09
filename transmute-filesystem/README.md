# transmute-filesystem

Cross-platform filesystem abstraction layer.

## Overview

Provides a platform-agnostic filesystem interface with two submodules:

| Submodule     | Purpose                                          |
|---|---|
| [core](core/) | Pure interface module - no external dependencies |
| [okio](okio/) | Okio-backed implementation for all KMP targets   |

## Quick Start

```kotlin
// Depend on the Okio implementation
commonMain.dependencies {
    implementation(project(":transmute-filesystem:okio"))
}
```

```kotlin
val fs: TransmuteFileSystem = OkioFileSystem(FileSystem.SYSTEM)

// Read a file
val data = fs.read(TPath.of("image.png"))

// Random-access read
val handle = fs.openRead(TPath.of("image.png"))
val header = ByteArray(8)
handle.read(header)
handle.close()

// Write a file
fs.write(TPath.of("output.png"), data, WriteMode.Create)
```

## Path Abstraction

`TPath` provides a cross-platform path representation with segment-based
construction, the `/` operator for joining, and properties for parent, name,
extension, and stem.

```kotlin
val path = TPath.of("images") / "photo.png"
path.name      // "photo.png"
path.extension // "png"
path.stem      // "photo"
path.parent    // TPath.of("images")
```

## I/O Abstractions

The suspending byte-stream types (`TSource`, `TSink`, `TChannel`) that pair with
filesystem operations live in `transmute-api` under `dev.transmute.io`. See the
[transmute-api README](../transmute-api/README.md) for details.

## Targets

Android, Desktop JVM, iOS - via Kotlin Multiplatform.
