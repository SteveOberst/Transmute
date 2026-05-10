# transmute-codec

The codec abstraction layer and composable pipeline system.

Published artifact: `com.github.SteveOberst.Transmute:transmute-codec:<version>`

## Overview

Defines the `Decoder`, `Encoder`, and `Codec` interfaces plus the composable
pipeline system for building media conversion pipelines. This is the backbone of
the **decode -> transform -> encode** flow.

## Key Types

### Codec Interfaces

| Type | Purpose |
|---|---|
| `Decoder<F, IR, D>` | Base decoder interface (decode, decodableFormats) |
| `Encoder<F, IR, O>` | Base encoder interface (encode, encodableFormats) |
| `Codec<F, IR, D, O>` | Unified decode + encode interface |

### Pipeline System

| Type | Purpose |
|---|---|
| `Pipeline<IN, OUT>` | Type alias for composable pipelines |
| `DecodePipeline<IN, IR>` | Decode pipeline (bytes -> IR) |
| `EncodePipeline<IR, OUT>` | Encode pipeline (IR -> encoded bytes) |
| `TransformPipeline<IR>` | Ordered, mutable pipeline of transforms |
| `Transform<IR>` | Single step in a transform pipeline |
| `TransformId` | Identifier for transforms |
| `PipelineHandler<IN, OUT>` | Composable pipeline step (`fun interface`) |
| `FluentPipeline` | Fluent pipeline builder DSL |

### Supporting Types

| Type | Purpose |
|---|---|
| `EncodedBytes<F>` | Encoded bytes tagged with resolved format |
| `Decoded<F, IR>` | Decode result: IR + resolved format |
| `DecodeRange` | Decode range selector (time or frame) |
| `OutputFormat<F>` | Output format selection: `ORIGINAL` or specific |
| `MetadataPolicy` | Controls metadata preservation during encoding |

## Usage

```kotlin
// Composable pipeline
val pipeline = ImageDecodePipeline then ImageTransformPipeline then ImageEncodePipeline
val result = pipeline.execute(inputBytes)
```

## Dependencies

- `transmute-common`
- `kotlinx-coroutines-core`

## Targets

Android, Desktop JVM, iOS - via Kotlin Multiplatform.
