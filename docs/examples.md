# Examples

Practical conversion and transformation recipes. All examples use `Transmute.Default` (the lazy no-plugin singleton). Swap it for a custom `Transmute` instance built with `Transmute { plugins { ... } }` when you need extra codecs.

## Image

### Convert PNG to JPEG

```kotlin
val jpeg = Transmute.image.to(ImageFormat.Jpeg) {
    encode { options(JpegEncodeOptions(quality = 0.85f)) }
}.transmute(pngBytes.asBytes())
// jpeg is EncodedBytes<ImageFormat.Jpeg>
val rawBytes: ByteArray = jpeg.bytes.data
```

### Scale to fit within bounds (preserve aspect ratio, no upscale)

```kotlin
val scaled = Transmute.image {
    scale(maxWidth = 1920, maxHeight = 1080)
}.transmute(source).bytes.data
```

### Resize to exact dimensions with Lanczos resampling

```kotlin
val resized = Transmute.image {
    resize(800, 600, filter = ResampleFilter.LANCZOS3)
}.transmute(source).bytes.data
```

### Crop, rotate, and convert to WebP in one pass

```kotlin
val out = Transmute.image.to(ImageFormat.Webp) {
    crop(x = 100, y = 50, width = 400, height = 300)
    rotate(degrees = 90)
}.transmute(source)
```

### Grayscale + brightness/contrast adjustment

```kotlin
val out = Transmute.image {
    grayscale()
    brightnessContrast(brightness = 20f, contrast = 1.2f)
}.transmute(source).bytes.data
```

### Apply blur and adjust opacity

```kotlin
val out = Transmute.image {
    blur(radius = 2)
    opacity(0.75f)
}.transmute(source).bytes.data
```

### Flip an image horizontally

```kotlin
val out = Transmute.image {
    flip(horizontal = true)
}.transmute(source).bytes.data
```

### Force output format via encode options (dynamic transmuter)

```kotlin
val png = Transmute.image {
    encode { options { outputFormat = OutputFormat.Exact(ImageFormat.Png) } }
}.transmute(jpegBytes.asBytes())
```

### Restrict accepted input formats

```kotlin
val transmuter = Transmute.image {
    decode { options { acceptedInputFormats += ImageFormat.Jpeg } }
    scale(640, 480)
}
```

### Copy result bytes into a pre-allocated buffer

```kotlin
val transmuter = Transmute.image { scale(640, 480) }
val buffer = ByteArray(1_000_000)
val written = transmuter.transmute(source, buffer) // returns bytes written
```

---

## Audio

### Convert WAV to MP3

```kotlin
val mp3 = Transmute.audio {
    encode { options { outputFormat = OutputFormat.Exact(AudioFormat.Mp3) } }
}.transmute(wavBytes.asBytes()).bytes.data
```

### Normalize and trim

```kotlin
val out = Transmute.audio {
    normalize(targetPeak = 0.95f)
    trim(startMs = 500, endMs = 10_000)
}.transmute(source).bytes.data
```

### Apply fade-in / fade-out

```kotlin
val out = Transmute.audio {
    fade(fadeInMs = 200, fadeOutMs = 500)
}.transmute(source).bytes.data
```

### Adjust volume in dB

```kotlin
val louder = Transmute.audio {
    gain(db = 6f)   // +6 dB louder
}.transmute(source).bytes.data
```

### Mix stereo to mono

```kotlin
val mono = Transmute.audio {
    mono()
}.transmute(source).bytes.data
```

### Resample to 48 kHz

```kotlin
val resampled = Transmute.audio {
    resample(targetSampleRate = 48_000)
}.transmute(source).bytes.data
```

### Change playback speed (no pitch shift)

```kotlin
val halfSpeed = Transmute.audio {
    speed(0.5f)
}.transmute(source).bytes.data
```

### Dynamic range compression

```kotlin
val compressed = Transmute.audio {
    compressor(thresholdDb = -20f, ratio = 4f, attackMs = 10f, releaseMs = 100f)
}.transmute(source).bytes.data
```

### Trim leading silence

```kotlin
val trimmed = Transmute.audio {
    silenceTrim(thresholdDb = -40f, minSilenceMs = 100, trimStart = true, trimEnd = false)
}.transmute(source).bytes.data
```

### Reverse audio

```kotlin
val reversed = Transmute.audio {
    reverse()
}.transmute(source).bytes.data
```

### Remap channels (stereo to left-only mono)

```kotlin
val leftOnly = Transmute.audio {
    channelMap(intArrayOf(0, 0))  // output ch0 = input ch0, output ch1 = input ch0
}.transmute(source).bytes.data
```

---

## Video

### Convert to MP4

```kotlin
val mp4 = Transmute.video {
    encode { options { outputFormat = OutputFormat.Exact(VideoFormat.Mp4) } }
}.transmute(source).bytes.data
```

### Scale to fit within 1280×720

```kotlin
val scaled = Transmute.video {
    resize(maxWidth = 1280, maxHeight = 720)
}.transmute(source).bytes.data
```

### Trim a clip

```kotlin
val clip = Transmute.video {
    trim(startMs = 5_000, endMs = 15_000)
}.transmute(source).bytes.data
```

### Change frame rate

```kotlin
val at30fps = Transmute.video {
    frameRate(targetFps = 30.0)
}.transmute(source).bytes.data
```

### Remove audio track

```kotlin
val silent = Transmute.video {
    removeAudio()
}.transmute(source).bytes.data
```

### Crop frames

```kotlin
val cropped = Transmute.video {
    crop(x = 0, y = 0, width = 1280, height = 720)
}.transmute(source).bytes.data
```

### Double playback speed

```kotlin
val fast = Transmute.video {
    speed(2.0f)
}.transmute(source).bytes.data
```

### Rotate 90 degrees

```kotlin
val rotated = Transmute.video {
    rotate(90)
}.transmute(source).bytes.data
```

### Chain multiple transforms

```kotlin
val out = Transmute.video {
    trim(startMs = 2_000, endMs = 60_000)
    resize(maxWidth = 1920, maxHeight = 1080)
    frameRate(24.0)
    encode { options { outputFormat = OutputFormat.Exact(VideoFormat.Mp4) } }
}.transmute(source).bytes.data
```

### Extract a thumbnail from the first frame

```kotlin
val thumbnail: EncodedBytes<ImageFormat> =
    Transmute.inspect.video.thumbnailFirstFrame(source)
val pngBytes = thumbnail.bytes.data
```

---

## One-Shot Dispatch (no transmuter setup)

For simple cases where you just want to re-encode without transforms:

```kotlin
val result: ByteArray = Transmute.transmute(TransmuteType.Image, inputBytes)
```

This uses the default decode/encode pipeline for the detected format. See [codec.md](codec.md) for more control.
