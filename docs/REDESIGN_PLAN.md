# Transmute API Redesign — v0.2.0 Plan

## Motivation

The current `ImageTransmuter(source: ByteArray)` couples source bytes to the builder at
construction, making transmuters single-use. In ShrinkIt (and similar consumers) we need
to configure a transmuter once (from a `Profile`) and apply it to many media items.
We also need a `wouldAffect(hint)` predicate so a media grid can filter items without
decoding them.

---

## 0. Semver bump: 0.1.1 → 0.2.0

All five modules (`transmute-core`, `transmute-image`, `transmute-audio`, `transmute-video`,
`transmute-api`) bump to `0.2.0`. This is a breaking API change.

---

## 1. `Transmuter<Self>` interface (transmute-core)

Change `transmute()` to take source bytes at call time:

```kotlin
// BEFORE
suspend fun transmute(): ByteArray
suspend fun transmuteInto(buffer: ByteArray, offset: Int): Int

// AFTER
suspend fun transmute(source: ByteArray): ByteArray
suspend fun transmuteInto(source: ByteArray, buffer: ByteArray, offset: Int = 0): Int
```

`wouldAffect` is NOT on the interface — hint types differ per media type.

---

## 2. Hint data classes

### `ImageHint` (transmute-image)
```kotlin
data class ImageHint(
    val width: Int? = null,
    val height: Int? = null,
    val format: ImageFormat? = null,
    val sizeBytes: Long? = null,
)
```

### `AudioHint` (transmute-audio)
```kotlin
data class AudioHint(
    val durationMs: Long? = null,
    val sampleRate: Int? = null,
    val channelCount: Int? = null,
    val format: AudioFormat? = null,
    val sizeBytes: Long? = null,
)
```

### `VideoHint` (transmute-video)
```kotlin
data class VideoHint(
    val width: Int? = null,
    val height: Int? = null,
    val fps: Double? = null,
    val durationMs: Long? = null,
    val format: VideoFormat? = null,
    val sizeBytes: Long? = null,
)
```

Hint types live in their respective media modules (not `transmute-core`) because they
reference `ImageFormat` / `AudioFormat` / `VideoFormat`.

---

## 3. Transform field visibility (all transforms — consistency requirement)

Every transform that has constructor parameters which drive a conditional transformation
must expose those parameters as `internal val` (not `private val`) so transmuters can
implement `wouldAffect` by inspecting the pipeline without reflection.

**Image transforms:**
| Class | Fields to widen |
|---|---|
| `ImageScaleTransform` | `maxWidth: Int`, `maxHeight: Int` |
| `ImageResizeTransform` | `targetWidth: Int`, `targetHeight: Int` |
| `ImageCropTransform` | `x: Int`, `y: Int`, `cropWidth: Int`, `cropHeight: Int` |
| `ImageRotateTransform` | `degrees: Int` |
| `ImageGrayscaleTransform` | (no params) |
| `ImageFlipTransform` | `horizontal: Boolean`, `vertical: Boolean` |
| `ImageBrightnessContrastTransform` | `brightness: Float`, `contrast: Float` |
| `ImageBlurTransform` | `radius: Float` |
| `ImageOpacityTransform` | `opacity: Float` |

**Audio transforms:**
| Class | Fields to widen |
|---|---|
| `AudioResampleTransform` | `targetSampleRate: Int` |
| `AudioChannelTransform` | `targetChannels: Int` |
| `AudioTrimTransform` | `startMs: Long`, `endMs: Long?` |
| `AudioSpeedTransform` | `speed: Float` |
| `AudioMetadataTransform` | (policy exposed via existing field) |

**Video transforms:**
| Class | Fields to widen |
|---|---|
| `VideoResizeTransform` | `maxWidth: Int`, `maxHeight: Int` |
| `VideoFrameRateTransform` | `targetFps: Double` |
| `VideoTrimTransform` | `startMs: Long`, `endMs: Long?` |
| `VideoRemoveAudioTransform` | (no params) |
| `VideoCropTransform` | `x: Int`, `y: Int`, `cropWidth: Int`, `cropHeight: Int` |
| `VideoRotateTransform` | `degrees: Int` |
| `VideoSpeedTransform` | `speed: Float` |
| `VideoMetadataTransform` | (policy exposed via existing field) |

---

## 4. `wouldAffect` — rules per transform type

### Conservative default
If a hint field is `null` (unknown) and a transform *might* act on it → return `true`.
It is always safer to include an item for processing than to silently skip it.

### Per-transform rules

**`ImageScaleTransform(maxWidth, maxHeight)`**
- `hint.width != null && hint.height != null && hint.width <= maxWidth && hint.height <= maxHeight` → `false`
- Otherwise → `true`

**`ImageResizeTransform(targetWidth, targetHeight)`**
- `hint.width != null && hint.height != null && hint.width == targetWidth && hint.height == targetHeight` → `false`
- Otherwise → `true`

**`ImageGrayscaleTransform`** → always `true` (no hint property for colour space)

**`ImageFlipTransform`** → always `true`

**`ImageRotateTransform`** → always `true`

**`ImageBrightnessContrastTransform`**
- `brightness == 0f && contrast == 0f` → `false`
- Otherwise → `true`

**`ImageBlurTransform`**
- `radius == 0f` → `false`
- Otherwise → `true`

**`ImageOpacityTransform`**
- `opacity == 1f` → `false`
- Otherwise → `true`

**`ImageCropTransform`** → always `true`

**`AudioResampleTransform(targetSampleRate)`**
- `hint.sampleRate != null && hint.sampleRate == targetSampleRate` → `false`
- Otherwise → `true`

**`AudioChannelTransform(targetChannels)`**
- `hint.channelCount != null && hint.channelCount == targetChannels` → `false`
- Otherwise → `true`

**`AudioTrimTransform`** → always `true`

**`AudioSpeedTransform`**
- `speed == 1f` → `false`
- Otherwise → `true`

**`VideoResizeTransform(maxWidth, maxHeight)`**
- `hint.width != null && hint.height != null && hint.width <= maxWidth && hint.height <= maxHeight` → `false`
- Otherwise → `true`

**`VideoFrameRateTransform(targetFps)`**
- `hint.fps != null && hint.fps <= targetFps` → `false`
- Otherwise → `true`

**`VideoTrimTransform`** → always `true`

**`VideoRemoveAudioTransform`** → always `true`

**`VideoCropTransform`** → always `true`

**`VideoRotateTransform`** → always `true`

**`VideoSpeedTransform`**
- `speed == 1f` → `false`
- Otherwise → `true`

### `wouldAffect` aggregation in transmuters

```kotlin
// ImageTransmuter
fun wouldAffect(hint: ImageHint): Boolean {
    if (metadataPolicy == MetadataPolicy.STRIP_ALL) return true
    if (outputFormat != null && outputFormat != hint.format) return true
    return pipeline.transforms.any { t ->
        when (t) {
            is ImageScaleTransform -> hint.width == null || hint.height == null
                || hint.width > t.maxWidth || hint.height > t.maxHeight
            is ImageResizeTransform -> hint.width == null || hint.height == null
                || hint.width != t.targetWidth || hint.height != t.targetHeight
            is ImageBrightnessContrastTransform -> t.brightness != 0f || t.contrast != 0f
            is ImageBlurTransform -> t.radius != 0f
            is ImageOpacityTransform -> t.opacity != 1f
            else -> true   // conservative
        }
    }
}
```

---

## 5. Transmuter constructor changes

```kotlin
// BEFORE
class ImageTransmuter(private val source: ByteArray)
class AudioTransmuter(private val source: ByteArray)
class VideoTransmuter(private val source: ByteArray)

// AFTER
class ImageTransmuter()        // no-arg; all configuration via builder methods
class AudioTransmuter()
class VideoTransmuter()
```

`transmute()` body is unchanged except source is the parameter, not `this.source`.

---

## 6. `Transmute` object factory changes

```kotlin
// NEW — reusable builders
fun image(): ImageTransmuter
fun audio(): AudioTransmuter
fun video(): VideoTransmuter

// KEPT — one-shot convenience (source passed immediately, block configures)
suspend fun image(source: ByteArray, block: ImageTransmuter.() -> Unit): ByteArray
suspend fun audio(source: ByteArray, block: AudioTransmuter.() -> Unit): ByteArray
suspend fun video(source: ByteArray, block: VideoTransmuter.() -> Unit): ByteArray

// NEW — apply pre-configured transmuter to one source
suspend fun image(source: ByteArray, transmuter: ImageTransmuter): ByteArray
suspend fun audio(source: ByteArray, transmuter: AudioTransmuter): ByteArray
suspend fun video(source: ByteArray, transmuter: VideoTransmuter): ByteArray

// REMOVED — was Transmute.image(source): ImageTransmuter (old source-binding factory)
//            callers now use Transmute.image() then call transmuter.transmute(source)
```

---

## 7. Usage examples

### Reusable transmuter (ShrinkIt profile pattern)
```kotlin
val imageTransmuter = Transmute.image().apply {
    scale(1080, 1080)
    quality(0.85f)
    metadata(MetadataPolicy.STRIP_ALL)
}

// Filter
val shouldProcess = imageTransmuter.wouldAffect(ImageHint(width = item.width, height = item.height, format = item.imageFormat))

// Apply
val compressed = imageTransmuter.transmute(imageBytes)
```

### One-shot convenience (unchanged ergonomics)
```kotlin
val result = Transmute.image(sourceBytes) {
    scale(512, 512)
    quality(0.8f)
}
```

### Apply pre-configured transmuter
```kotlin
val result = Transmute.image(sourceBytes, imageTransmuter)
```

---

## 8. Files to modify

| File | Change |
|---|---|
| `transmute-core/…/Transformers.kt` | Update `Transmuter<Self>` interface signatures |
| `transmute-image/…/ImageTransforms.kt` | Widen field visibility |
| `transmute-image/…/ImageHint.kt` | NEW — create |
| `transmute-audio/…/AudioTransforms.kt` | Widen field visibility |
| `transmute-audio/…/AudioHint.kt` | NEW — create |
| `transmute-video/…/VideoTransforms.kt` | Widen field visibility |
| `transmute-video/…/VideoCropTransform.kt` | Widen field visibility |
| `transmute-video/…/VideoRotateTransform.kt` | Widen field visibility |
| `transmute-video/…/VideoSpeedTransform.kt` | Widen field visibility |
| `transmute-video/…/VideoHint.kt` | NEW — create |
| `transmute-api/…/Transmute.kt` | Redesign all three transmuters + Transmute object |
| All `build.gradle.kts` files | Bump version 0.1.1 → 0.2.0 |

---

## Implementation Status

- [ ] Plan saved
- [ ] Read all transform source files
- [ ] Update `Transmuter<Self>` interface
- [ ] Create `ImageHint`, `AudioHint`, `VideoHint`
- [ ] Widen all transform field visibility (image)
- [ ] Widen all transform field visibility (audio)
- [ ] Widen all transform field visibility (video)
- [ ] Redesign `ImageTransmuter`
- [ ] Redesign `AudioTransmuter`
- [ ] Redesign `VideoTransmuter`
- [ ] Update `Transmute` object
- [ ] Bump versions
- [ ] Build and verify
