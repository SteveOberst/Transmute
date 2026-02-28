# transmute-video

Video domain module — formats, codecs, intermediate representation, and transforms.

## Overview

Defines video container formats, the `VideoIR` intermediate representation,
and video-specific transforms. Reuses `ImageIR` types for video frames and
`AudioIR` types for audio tracks within containers.

## Key Types

### Formats & IR

| Type | Purpose |
|------|---------|
| `VideoFormat` | Sealed interface: `Mp4`, `Webm`, `Mov`, `Avi`, `Mkv`, `Unknown` |
| `VideoIR` | Intermediate representation (videoTrack, audioTrack, durationMs, metadata) |
| `VideoTrack` | Width, height, frameRate, frames via `FrameStream` |
| `FrameStream` / `ListFrameStream` | Pull-based streaming access to decoded frames |
| `VideoFrame` | Single frame: PixelBuffer, width, height, pixelFormat, timestampMs |
| `AudioTrack` | Audio within a video container (samples, stream) |
| `VideoMetadata` | Title, artist, durationMs, bitrateKbps, app metadata |

### Codecs

| Type | Purpose |
|------|---------|
| `VideoCodec` / `VideoDecoder` / `VideoEncoder` | Codec interfaces |
| `VideoDecoderRegistry` / `VideoEncoderRegistry` | Registries with mutable variants for plugins |
| `VideoDecodeOptions` / `VideoEncodeOptions` | Sealed option hierarchies |
| `VideoFormatDetector` | Format detection from bytes |

### Transforms

| Transform | DSL | Purpose |
|-----------|-----|---------|
| `VideoTrimTransform` | `trim` | Trim to time range |
| `VideoResizeTransform` | `resize` | Resize frames, preserve aspect ratio |
| `VideoFrameRateTransform` | `frameRate` | Change frame rate |
| `VideoRemoveAudioTransform` | `removeAudio` | Strip audio track |
| `VideoCropTransform` | `crop` | Crop frames to sub-region |
| `VideoSpeedTransform` | `speed` | Playback speed adjustment |
| `VideoRotateTransform` | `rotate` | Rotate frames 90°/180°/270° |

## Dependencies

- `transmute-codec`
- `transmute-image` (implementation — for pixel buffer types)
- `transmute-audio` (implementation — for audio track types)
- `kotlinx-coroutines-core`

## Targets

Android, Desktop JVM, iOS — via Kotlin Multiplatform.
