# Documentation

Transmute is designed as a small, stable top-level API (`Transmute`) backed by:

- `Transmute.codec()` for one-shot decode/encode
- `Transmute.inspect()` for format detection and inspection
- `Transmute.structure` for reading/writing raw file structures (headers, chunks, atoms)
- Transmuters (`Transmute.image { ... }`, `Transmute.audio { ... }`, `Transmute.video { ... }`) for decode -> transforms -> encode

## Most Common Tasks

- Convert + transform: `docs/examples.md`
- Custom decode/encode pipelines (typed handler chains): `docs/pipelines.md`
- One-shot decode/encode: `docs/codec.md`
- Format detection + scoped extraction (video thumbnail/audio): `docs/inspect.md`, `docs/format-detection.md`
- Read/write file structures without decoding pixel/sample data: `docs/structures.md`

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
- `docs/transforms/README.md`

## Operations

- `docs/logging.md`
- `docs/extending.md` (custom codecs, transforms & structure readers)
