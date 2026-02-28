# transmute-filesystem:core

Pure interface module for the cross-platform filesystem abstraction.

## Overview

Defines the filesystem interface and supporting types with **zero external
dependencies**. Designed to be implemented by any backend (Okio, `java.nio`,
platform-native APIs, etc.).

## Key Types

### Filesystem

| Type | Purpose |
|------|---------|
| `TransmuteFileSystem` | Main interface: read, write, openRead, openWrite, list, mkdir, delete, move, copy |
| `ReadHandle` | Random-access read handle (read, seek, position, size) |
| `WriteHandle` | Write handle (write, flush) |
| `FileMetadata` | Size, timestamps, isRegularFile, isDirectory, isSymlink |
| `WriteMode` | `Create`, `Overwrite`, `Append` |

### Path

| Type | Purpose |
|------|---------|
| `TPath` | Cross-platform path: segments + optional root, `/` operator, resolve, parent, name, extension, stem |

### Testing

| Type | Purpose |
|------|---------|
| `InMemoryFileSystem` | In-memory implementation for unit testing |

### Exceptions

`FileNotFoundException`, `FileAlreadyExistsException`, `NotDirectoryException`

## Dependencies

None — pure interface module.

## Targets

Android, Desktop JVM, iOS — via Kotlin Multiplatform.
