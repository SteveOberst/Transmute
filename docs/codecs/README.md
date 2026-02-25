# Codecs

This directory contains per-format notes and platform support.

Each format page documents codec (decode/encode) support **and** structure reading support where available. Structure readers parse the raw binary layout of a file into typed Kotlin data classes without decoding pixel or sample data — see `docs/structures.md`.

## Image

| Format | Codec | Structure | Page |
|--------|-------|-----------|------|
| JPEG | ✅ | ✅ | `docs/codecs/jpeg.md` |
| PNG | ✅ | ✅ | `docs/codecs/png.md` |
| WebP | ✅ | — | `docs/codecs/webp.md` |
| HEIF | ✅ | — | `docs/codecs/heif.md` |
| AVIF | ✅ | — | `docs/codecs/avif.md` |
| GIF | ✅ | — | `docs/codecs/gif.md` |
| BMP | ✅ | ✅ | `docs/codecs/bmp.md` |
| TIFF | ✅ | — | `docs/codecs/tiff.md` |

## Audio

| Format | Codec | Structure | Page |
|--------|-------|-----------|------|
| WAV | ✅ | ✅ | `docs/codecs/wav.md` |
| MP3 | ✅ | ✅ | `docs/codecs/mp3.md` |
| AAC | ✅ | — | `docs/codecs/aac.md` |
| FLAC | ✅ | ✅ | `docs/codecs/flac.md` |
| OGG | ✅ | — | `docs/codecs/ogg.md` |
| Opus | ✅ | — | `docs/codecs/opus.md` |
| M4A | ✅ | — | `docs/codecs/m4a.md` |

## Video

| Format | Codec | Structure | Page |
|--------|-------|-----------|------|
| MP4 | ✅ | — | `docs/codecs/mp4.md` |
| MOV | ✅ | — | `docs/codecs/mov.md` |
| WebM | ✅ | — | `docs/codecs/webm.md` |
| AVI | ✅ | — | `docs/codecs/avi.md` |
| MKV | ✅ | — | `docs/codecs/mkv.md` |

