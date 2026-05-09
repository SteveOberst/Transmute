# Transforms

Transforms operate on an intermediate representation (IR) between the decode and encode stages. They are applied in order and are composable.

## Quick reference

### Image transforms (9)

| DSL name | Class | Description |
|---|---|---|
| `scale(maxWidth, maxHeight)` | `ImageScaleTransform` | Scale to fit within bounds, preserving aspect ratio. No upscaling. |
| `resize(w, h, filter?, allowUpscale?)` | `ImageResizeTransform` | Resize to exact dimensions with a configurable resample filter. |
| `crop(x, y, width, height)` | `ImageCropTransform` | Crop to a rectangular sub-region. |
| `rotate(degrees)` | `ImageRotateTransform` | Rotate clockwise by 90, 180, or 270 deg. |
| `grayscale()` | `ImageGrayscaleTransform` | Convert to grayscale using BT.709 luma coefficients. |
| `flip(horizontal?, vertical?)` | `ImageFlipTransform` | Flip horizontally and/or vertically. |
| `brightnessContrast(brightness?, contrast?)` | `ImageBrightnessContrastTransform` | Adjust brightness (-255..+255) and contrast (0..3). |
| `blur(radius?)` | `ImageBlurTransform` | Apply box blur. Radius 1 = 3x3 kernel. |
| `opacity(opacity)` | `ImageOpacityTransform` | Adjust alpha channel opacity (0.0-1.0). |

### Audio transforms (11)

| DSL name | Class | Description |
|---|---|---|
| `normalize(targetPeak?)` | `AudioNormalizeTransform` | Normalize peak amplitude to a target level (default 0.95). |
| `resample(targetSampleRate)` | `AudioResampleTransform` | Resample to a different sample rate (linear interpolation). |
| `fade(fadeInMs?, fadeOutMs?)` | `AudioFadeTransform` | Apply fade-in and/or fade-out amplitude envelopes. |
| `trim(startMs, endMs?)` | `AudioTrimTransform` | Trim to a time range. Omit `endMs` to trim to the end. |
| `gain(db)` | `AudioGainTransform` | Apply volume gain/attenuation in decibels. |
| `mono()` | `AudioMonoTransform` | Mix down to mono by averaging channels. |
| `reverse()` | `AudioReverseTransform` | Reverse playback direction. |
| `speed(speed)` | `AudioSpeedTransform` | Change playback speed without pitch shift (SOLA time-stretch). |
| `silenceTrim(thresholdDb?, minSilenceMs?, trimStart?, trimEnd?)` | `AudioSilenceTrimTransform` | Remove silence from start and/or end. |
| `compressor(thresholdDb?, ratio?, attackMs?, releaseMs?, makeupGainDb?)` | `AudioCompressorTransform` | Apply dynamic range compression. |
| `channelMap(mapping)` | `AudioChannelMapTransform` | Remap audio channels by output->source index array. |

### Video transforms (7)

| DSL name | Class | Description |
|---|---|---|
| `trim(startMs, endMs?)` | `VideoTrimTransform` | Trim to a time range. Omit `endMs` to keep to the end. |
| `resize(maxWidth, maxHeight)` | `VideoResizeTransform` | Resize frames to fit within bounds, preserving aspect ratio. |
| `frameRate(targetFps)` | `VideoFrameRateTransform` | Change the frame rate. |
| `removeAudio()` | `VideoRemoveAudioTransform` | Strip the audio track. |
| `crop(x, y, width, height)` | `VideoCropTransform` | Crop frames to a rectangular sub-region. |
| `speed(speed)` | `VideoSpeedTransform` | Change playback speed (adjusts both frame timing and audio). |
| `rotate(degrees)` | `VideoRotateTransform` | Rotate frames by 90, 180, or 270 deg clockwise. |

## Applying transforms

### DSL shorthand (recommended)

Extension functions on the builder are the most concise approach:

```kotlin
Transmute.image {
    scale(1920, 1080)
    rotate(90)
    grayscale()
}.transmute(source)

Transmute.audio {
    normalize(targetPeak = 0.9f)
    trim(startMs = 1_000, endMs = 30_000)
    fade(fadeInMs = 500, fadeOutMs = 500)
}.transmute(source)

Transmute.video {
    trim(0, 60_000)
    resize(1280, 720)
    frameRate(24.0)
}.transmute(source)
```

### Explicit transform block

```kotlin
Transmute.image {
    transform {
        add(ImageScaleTransform(1920, 1080))
        add(ImageRotateTransform(90))
    }
}.transmute(source)
```

### Transformers factory

`Transformers` is a catalog object useful when building transforms programmatically:

```kotlin
val myTransforms = listOf(
    Transformers.image().scale(800, 600),
    Transformers.image().grayscale(),
)

Transmute.image {
    transform { myTransforms.forEach { add(it) } }
}.transmute(source)
```

### Insert before a specific type

```kotlin
Transmute.video {
    transform {
        add(Transformers.video().resize(640, 480))
        before<VideoResizeTransform>(Transformers.video().trim(0, 5_000))  // insert trim before resize
    }
}
```

## Resample filters (image resize)

| Filter | Description |
|---|---|
| `ResampleFilter.NEAREST` | Nearest-neighbour - fastest, aliased |
| `ResampleFilter.BILINEAR` | Bilinear - smooth, fast |
| `ResampleFilter.BICUBIC_MITCHELL` | Bicubic Mitchell (default) - balanced quality/speed |
| `ResampleFilter.CATMULL_ROM` | Catmull-Rom - sharper bicubic |
| `ResampleFilter.LANCZOS3` | Lanczos3 - highest quality; anti-aliased for downscaling |

```kotlin
Transmute.image {
    resize(800, 600, filter = ResampleFilter.LANCZOS3, allowUpscale = false)
}
```

## Per-transform documentation

- [transforms/image/](image/) - Image transform details
- [transforms/audio/](audio/) - Audio transform details
- [transforms/video/](video/) - Video transform details
