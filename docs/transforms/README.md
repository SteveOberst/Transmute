# Transforms

All transforms are platform-independent, stateless operations on intermediate
representations (IRs). They require no native dependencies and work identically
on Android, Desktop/JVM, and iOS.

## Image Transforms (9)

| Class | DSL | Description | Docs |
|-------|-----|-------------|------|
| `ImageScaleTransform` | `scale` | Fit within bounds, preserve aspect ratio | [scale.md](image/scale.md) |
| `ImageResizeTransform` | `resize` | Exact resize with resample filter (Lanczos3, Mitchell, etc.) | [resize.md](image/resize.md) |
| `ImageCropTransform` | `crop` | Crop to sub-region | [crop.md](image/crop.md) |
| `ImageRotateTransform` | `rotate` | Auto-rotate from EXIF orientation | [rotate.md](image/rotate.md) |
| `ImageGrayscaleTransform` | `grayscale` | BT.709 luma conversion | [grayscale.md](image/grayscale.md) |
| `ImageFlipTransform` | `flip` | Mirror horizontally / vertically | [flip.md](image/flip.md) |
| `ImageBrightnessContrastTransform` | `brightnessContrast` | Adjust brightness (−255..+255) and contrast (0..3) | [brightness-contrast.md](image/brightness-contrast.md) |
| `ImageBlurTransform` | `blur` | Box blur with configurable radius | [blur.md](image/blur.md) |
| `ImageOpacityTransform` | `opacity` | Adjust alpha channel | [opacity.md](image/opacity.md) |

## Audio Transforms (11)

| Class | DSL | Description | Docs |
|-------|-----|-------------|------|
| `AudioNormalizeTransform` | `normalize` | Peak amplitude normalization | [normalize.md](audio/normalize.md) |
| `AudioResampleTransform` | `resample` | Resample to target sample rate | [resample.md](audio/resample.md) |
| `AudioFadeTransform` | `fade` | Fade-in / fade-out envelopes | [fade.md](audio/fade.md) |
| `AudioTrimTransform` | `trim` | Trim to time range | [trim.md](audio/trim.md) |
| `AudioGainTransform` | `gain` | Volume gain in dB | [gain.md](audio/gain.md) |
| `AudioMonoTransform` | `mono` | Stereo → mono | [mono.md](audio/mono.md) |
| `AudioReverseTransform` | `reverse` | Reverse playback | [reverse.md](audio/reverse.md) |
| `AudioSpeedTransform` | `speed` | Playback speed (SOLA time-stretch, no pitch change) | [speed.md](audio/speed.md) |
| `AudioSilenceTrimTransform` | `silenceTrim` | Trim silence from start / end | [silence-trim.md](audio/silence-trim.md) |
| `AudioCompressorTransform` | `compressor` | Dynamic range compressor | [compressor.md](audio/compressor.md) |
| `AudioChannelMapTransform` | `channelMap` | Remap audio channels | [channel-map.md](audio/channel-map.md) |

## Video Transforms (7)

| Class | DSL | Description | Docs |
|-------|-----|-------------|------|
| `VideoTrimTransform` | `trim` | Trim to time range | [trim.md](video/trim.md) |
| `VideoResizeTransform` | `resize` | Fit within bounds, preserve aspect ratio | [resize.md](video/resize.md) |
| `VideoFrameRateTransform` | `frameRate` | Change frame rate | [frame-rate.md](video/frame-rate.md) |
| `VideoRemoveAudioTransform` | `removeAudio` | Strip audio track | [remove-audio.md](video/remove-audio.md) |
| `VideoCropTransform` | `crop` | Crop frames to sub-region | [crop.md](video/crop.md) |
| `VideoSpeedTransform` | `speed` | Playback speed (adjusts frames + audio) | [speed.md](video/speed.md) |
| `VideoRotateTransform` | `rotate` | Rotate by 90°, 180°, or 270° | [rotate.md](video/rotate.md) |

## Adding a Custom Transform

See [extending.md](../extending.md) and [CONTRIBUTING.md](../../CONTRIBUTING.md#adding-a-new-transform).

