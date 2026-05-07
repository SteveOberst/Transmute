# Documentation

This directory contains the full Transmute documentation.

## Getting Started

- Start with the [project README](../README.md) for a quick-start overview and setup instructions.
- [examples.md](examples.md) - Practical conversion and transformation examples for all three domains.

## Core Concepts

| File | Contents |
|---|---|
| [examples.md](examples.md) | Common conversion recipes: image, audio, video |
| [pipelines.md](pipelines.md) | How the decode -> transform -> encode pipeline works; customising stages |
| [codec.md](codec.md) | One-shot decode/encode via `Transmute.codec` without building a transmuter |
| [inspect.md](inspect.md) | Format detection, metadata extraction, structure reading, video thumbnails |
| [format-detection.md](format-detection.md) | Deep dive on automatic format detection logic |
| [structures.md](structures.md) | Parsing and round-tripping raw file structures (chunks, atoms, headers) |

## Extension Points

| File | Contents |
|---|---|
| [plugins.md](plugins.md) | Instance-based API and the plugin system |
| [extending.md](extending.md) | Writing custom codecs, transforms, structure decoders, and plugins |
| [logging.md](logging.md) | Global and per-operation logging configuration |

## Format Reference

| File | Contents |
|---|---|
| [codecs/README.md](codecs/README.md) | All supported formats with platform availability matrix |
| [codecs/jpeg.md](codecs/jpeg.md) | JPEG |
| [codecs/png.md](codecs/png.md) | PNG |
| [codecs/webp.md](codecs/webp.md) | WebP |
| [codecs/heif.md](codecs/heif.md) | HEIF |
| [codecs/avif.md](codecs/avif.md) | AVIF |
| [codecs/gif.md](codecs/gif.md) | GIF |
| [codecs/bmp.md](codecs/bmp.md) | BMP |
| [codecs/tiff.md](codecs/tiff.md) | TIFF |
| [codecs/mp3.md](codecs/mp3.md) | MP3 |
| [codecs/aac.md](codecs/aac.md) | AAC |
| [codecs/wav.md](codecs/wav.md) | WAV |
| [codecs/ogg.md](codecs/ogg.md) | OGG |
| [codecs/flac.md](codecs/flac.md) | FLAC |
| [codecs/m4a.md](codecs/m4a.md) | M4A |
| [codecs/opus.md](codecs/opus.md) | Opus |
| [codecs/mp4.md](codecs/mp4.md) | MP4 |
| [codecs/webm.md](codecs/webm.md) | WebM |
| [codecs/mov.md](codecs/mov.md) | MOV |
| [codecs/avi.md](codecs/avi.md) | AVI |
| [codecs/mkv.md](codecs/mkv.md) | MKV |

## Transform Reference

| File | Contents |
|---|---|
| [transforms/README.md](transforms/README.md) | Complete index of all 27 transforms with parameters |
| [transforms/image/](transforms/image/) | Image transforms (scale, resize, crop, rotate, ...) |
| [transforms/audio/](transforms/audio/) | Audio transforms (normalize, trim, fade, ...) |
| [transforms/video/](transforms/video/) | Video transforms (trim, resize, frameRate, ...) |
