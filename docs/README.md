# Documentation

Transmute is designed as a small, stable top-level API (`Transmute`) backed by:

- `Transmute.codec` for one-shot decode/encode
- `Transmute.inspect` for format detection and inspection
- `Transmute.structure` for reading/writing raw file structures (headers, chunks, atoms)
- Transmuters (`Transmute.image { ... }`, `Transmute.audio { ... }`, `Transmute.video { ... }`) for decode -> transforms -> encode
- Instance-based API with plugin system: `Transmute { plugins { install(GStreamer) } }`
- Suspending I/O via `TSource`, `TSink`, `TChannel` — non-blocking on every platform

## Most Common Tasks

- Convert + transform: `docs/examples.md`
- Custom decode/encode pipelines (typed handler chains): `docs/pipelines.md`
- One-shot decode/encode: `docs/codec.md`
- Format detection + scoped extraction (video thumbnail/audio): `docs/inspect.md`, `docs/format-detection.md`
- Read/write file structures without decoding pixel/sample data: `docs/structures.md`
- Instance-based API & plugins: `docs/plugins.md`

## Getting Started

- `README.md` (overview + quick start)
- `docs/examples.md`
- `docs/pipelines.md`

## Codec + Inspect

- `docs/codec.md`
- `docs/inspect.md`
- `docs/format-detection.md`

## Structure

- `docs/structures.md` (parsing files into typed structures)

## Codecs + Transforms

- `docs/codecs/README.md`
- `docs/transforms/README.md` (complete index of all 27 transforms with DSL names and links)

## Operations

- `docs/logging.md`
- `docs/extending.md` (custom codecs, transforms & structure readers)
- `docs/plugins.md` (instance-based API & plugin system)
- `docs/gstreamer.md` (optional GStreamer integration for advanced codecs)

## Modules

Each `transmute-*` directory has its own README:

| Module | Purpose |
|--------|---------|
| [transmute-api](../transmute-api/README.md) | Public API facade — main entry point |
| [transmute-codec](../transmute-codec/README.md) | Codec abstraction layer & pipeline system |
| [transmute-common](../transmute-common/README.md) | Shared infrastructure (context, logging) |
| [transmute-audio](../transmute-audio/README.md) | Audio formats, codecs, transforms |
| [transmute-image](../transmute-image/README.md) | Image formats, codecs, transforms |
| [transmute-video](../transmute-video/README.md) | Video formats, codecs, transforms |
| [transmute-structure](../transmute-structure/README.md) | Concrete structure readers (20 formats) |
| [transmute-model](../transmute-model/README.md) | Data model layer (core, identify, structure, view, stream, metadata, diagnostics) |
| [transmute-filesystem](../transmute-filesystem/README.md) | Cross-platform filesystem abstraction |
| [transmute-gstreamer](../transmute-plugins/gstreamer/README.md) | Optional GStreamer integration plugin |
