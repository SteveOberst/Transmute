# transmute-common

Shared infrastructure and cross-cutting concerns for all Transmute modules.

Published artifact: `com.github.SteveOberst.Transmute:transmute-common:<version>`

## Overview

Provides the composition root, pipeline runtime context, logging, configuration,
and a multiplatform `Closeable` interface.

## Key Types

| Type | Purpose |
|---|---|
| `TransmuteContext` | Composition root: logger, extras map, pipeline context factory |
| `PipelineContext` | Runtime context passed through every pipeline handler |
| `TransmuteLogger` | Structured logger interface (debug, info, warn, error) |
| `LogLevel` | Log severity: `DEBUG`, `INFO`, `WARN`, `ERROR`, `OFF` |
| `TransmuteLogging` | Global logging configuration singleton |
| `TransmuteConfig` | Global runtime configuration singleton |
| `Closeable` | Simple multiplatform close contract |

## Usage

```kotlin
val context = TransmuteContext(
    logger = TransmuteLogger.Noop
)

context.logger.info("Processing file...")
```

## Dependencies

- `transmute-model:core`

## Targets

Android, Desktop JVM, iOS - via Kotlin Multiplatform.
