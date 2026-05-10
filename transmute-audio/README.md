# transmute-audio

Audio domain module - formats, codecs, intermediate representation, and transforms.

Published artifact: `com.github.SteveOberst.Transmute:transmute-audio:<version>`

## Overview

Defines audio-specific formats, the `AudioIR` intermediate representation,
platform-specific decoders/encoders, and built-in audio transforms. WAV support
is built-in on all platforms; desktop JVM adds MP3, FLAC, and OGG/Vorbis via
JLayer, jump3r, jflac-codec, and jorbis.

## Key Types

### Formats & IR

| Type | Purpose |
|---|---|
| `AudioFormat` | Sealed interface: `Mp3`, `Aac`, `Wav`, `Ogg`, `Flac`, `M4a`, `Opus`, `Unknown` |
| `AudioIR` | Intermediate representation (samples, sampleRate, channelCount, durationMs) |
| `AudioSamples` | In-memory float audio sample buffer |
| `SampleStream` | Pull-based streaming access to decoded samples |
| `AudioMetadata` | Title, artist, album, genre, bitrateKbps, etc. |

### Codecs

| Type | Purpose |
|---|---|
| `AudioCodec` | Unified audio codec interface |
| `AudioDecoder` / `AudioEncoder` | Split codec interfaces |
| `AudioDecoderRegistry` / `AudioEncoderRegistry` | Registries with `MutableAudioDecoderRegistry` / `MutableAudioEncoderRegistry` for plugins |
| `AudioDecodeOptions` / `AudioEncodeOptions` | Sealed option hierarchies |
| `AudioFormatDetector` | Format detection from bytes |

### Transforms

| Transform | DSL | Purpose |
|---|---|---|
| `AudioNormalizeTransform` | `normalize` | Peak amplitude normalization |
| `AudioResampleTransform` | `resample` | Resample to target sample rate |
| `AudioFadeTransform` | `fade` | Fade-in / fade-out envelopes |
| `AudioTrimTransform` | `trim` | Trim to time range |
| `AudioGainTransform` | `gain` | Volume gain in dB |
| `AudioMonoTransform` | `mono` | Stereo -> mono |
| `AudioReverseTransform` | `reverse` | Reverse playback |
| `AudioSpeedTransform` | `speed` | Playback speed (SOLA, no pitch change) |
| `AudioSilenceTrimTransform` | `silenceTrim` | Trim leading/trailing silence |
| `AudioCompressorTransform` | `compressor` | Dynamic range compression |
| `AudioChannelMapTransform` | `channelMap` | Remap audio channels |

### Platform Codecs

- **Common:** `WavCodec`
- **Desktop JVM:** JLayer MP3 decode, jump3r MP3 encode, jflac FLAC decode, jorbis OGG/Vorbis decode
- **Android:** jump3r MP3 encode

## Dependencies

- `transmute-codec`
- `kotlinx-coroutines-core`

## Targets

Android, Desktop JVM, iOS - via Kotlin Multiplatform.
