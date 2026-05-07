# transmute-model

Umbrella module for the Transmute data model layer.

## Overview

`transmute-model` aggregates all model submodules into a single dependency.
It contains no source code of its own - instead it re-exports:

| Submodule | Purpose |
|---|---|
| [core](core/) | Foundation types: `Bytes`, `MediaFormat`, serialization primitives, typed wrappers |
| [identify](identify/) | Format identification: magic signatures, confidence levels, brands |
| [structure](structure/) | Typed data models for every supported media format's binary layout |
| [view](view/) | Read-only and mutable views over structure models, plus `.edit {}` sugar |
| [stream](stream/) | Stream descriptors: codec info, video/audio/image per-stream metadata |
| [metadata](metadata/) | Structured metadata: tags, keys, values, sources (ID3, EXIF, XMP, etc.) |
| [diagnostics](diagnostics/) | Inspection issues: severity, codes, contextual information |

## Quick Start

Depend on the umbrella module to pull in everything:

```kotlin
commonMain.dependencies {
    api(project(":transmute-model"))
}
```

Or pick individual submodules for a smaller dependency footprint.

## Targets

Android, Desktop JVM, iOS - via Kotlin Multiplatform.
