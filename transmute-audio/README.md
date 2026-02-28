# transmute-audio

Audio domain module — formats, codecs, intermediate representation, and transforms.

## Overview

Defines audio-specific formats, the `AudioIR` intermediate representation,
platform-specific decoders/encoders, and built-in audio transforms. WAV support
is built-in on all platforms; desktop JVM adds MP3, FLAC, and OGG/Vorbis via
JLayer, jump3r, jflac-codec, and jorbis.

## Key Types

### Formats & IR

| Type | Purpose |
|------|---------|
| `AudioFormat` | Sealed interface: `Mp3`, `Aac`, `Wav`, `Ogg`, `Flac`, `M4a`, `Opus`, `Unknown` |
| `AudioIR` | Intermediate representation (samples, sampleRate, channelCount, durationMs) |
| `AudioSamples` | In-memory float audio sample buffer |
| `SampleStream` | Pull-based streaming access to decoded samples |
| `AudioMetadata` | Title, artist, album, genre, bitrateKbps, etc. |

### Codecs

| Type | Purpose |
|------|---------|
| `AudioCodec` | Unified audio codec interface |
| `AudioDecoder` / `AudioEncoder` | Split codec interfaces |
| `AudioDecoderRegistry` / `AudioEncoderRegistry` | Registries with `MutableAudioDecoderRegistry` / `MutableAudioEncoderRegistry` for plugins |
| `AudioDecodeOptions` / `AudioEncodeOptions` | Sealed option hierarchies |
| `AudioFormatDetector` | Format detection from bytes |

### Transforms

| Transform | Purpose |
|-----------|---------|
| `AudioSpeedTransform` | Playback speed adjustment |
| `AudioSilenceTrimTransform` | Trim leading/trailing silence |
| `AudioCompressorTransform` | Dynamic range compression |
| `AudioChannelMapTransform` | Mono ↔ stereo channel mapping |

### Platform Codecs

- **Common:** `WavCodec`
- **Desktop JVM:** JLayer MP3 decode, jump3r MP3 encode, jflac FLAC decode, jorbis OGG/Vorbis decode
- **Android:** jump3r MP3 encode

## Dependencies

- `transmute-codec`
- `kotlinx-coroutines-core`

## Targets

Android, Desktop JVM, iOS — via Kotlin Multiplatform.
