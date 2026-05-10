# transmute-model

Umbrella module for the Transmute data model layer.

Published artifact: `com.github.SteveOberst.Transmute:transmute-model:<version>`

## Overview

`transmute-model` aggregates all model submodules into a single dependency.
It contains no source code of its own - instead it re-exports:

| Submodule | Purpose |
|---|---|
| [core](core/) | Foundation types: `Bytes`, `MediaFormat`, serialization primitives, typed wrappers |
| [identify](identify/) | Format identification: magic signatures, confidence levels, brands |
| [structure](structure/) | Typed data models for every supported media format's binary layout |
| [metadata](metadata/) | Structured metadata: tags, keys, values, sources (ID3, EXIF, XMP, etc.) |

## Quick Start

Depend on the umbrella module to pull in everything:

```kotlin
dependencies {
    implementation("com.github.SteveOberst.Transmute:transmute-model:<version>")
}
```

Or pick individual submodules for a smaller dependency footprint.

## Targets

Android, Desktop JVM, iOS - via Kotlin Multiplatform.
