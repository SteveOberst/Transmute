# Format Detection

Transmute detects formats by reading magic bytes and container signatures from the raw byte stream — no file extension required.

## Entry points

```kotlin
// Universal: detects image, audio, and video formats
val format: MediaFormat<*, *> = Transmute.inspect.detectFormat(bytes)

// Domain-specific: only considers formats within that domain
val imageFormat: ImageFormat = Transmute.inspect.image.detectFormat(bytes)
val audioFormat: AudioFormat = Transmute.inspect.audio.detectFormat(bytes)
val videoFormat: VideoFormat = Transmute.inspect.video.detectFormat(bytes)

// Suspending TSource overload (reads the source once)
val format = Transmute.inspect.detectFormat(source)
```

All accept `Bytes`, `ByteArray`, or `TSource`.

## Detection priority

The universal `detectFormat()` uses the following priority order:

1. **ISO Base Media File Format (ISOBMFF / BMFF)** — identified by the `ftyp` box signature at bytes 4–7. This covers MP4, MOV, M4A, HEIF, HEIC, and AVIF.
   - Major brand `qt  ` → `VideoFormat.Mov`
   - Contains a video track → `VideoFormat.Mp4`
   - Contains an audio track only → `AudioFormat.M4a`
   - Image brand (HEIF/HEIC/AVIF) → `ImageFormat.Heif`, `ImageFormat.Heic`, or `ImageFormat.Avif`

2. **Image format detection** — checked against the built-in image format detector using magic bytes.

3. **Video format detection** — checked against the built-in video format detector.

4. **Audio format detection** — checked against the built-in audio format detector.

5. **`UnknownFormat`** — returned if nothing matches.

## Return values

All detectors return typed sentinels for unknown results rather than `null`:

| Unknown sentinel | Type |
|-----------------|------|
| `ImageFormat.Unknown` | `ImageFormat` |
| `AudioFormat.Unknown` | `AudioFormat` |
| `VideoFormat.Unknown` | `VideoFormat` |
| `UnknownFormat` | `MediaFormat<*,*>` (from universal detector) |

```kotlin
val format = Transmute.inspect.detectFormat(bytes)
if (format == UnknownFormat) {
    // cannot determine format
}

val imgFmt = Transmute.inspect.image.detectFormat(bytes)
if (imgFmt == ImageFormat.Unknown) {
    // not a recognised image format
}
```

## Magic byte signatures

| Format | Detection method |
|--------|----------------|
| JPEG   | `FF D8 FF` |
| PNG    | `89 50 4E 47 0D 0A 1A 0A` |
| WebP   | `52 49 46 46 … 57 45 42 50` (RIFF…WEBP) |
| GIF    | `47 49 46 38` (GIF8) |
| BMP    | `42 4D` (BM) |
| TIFF   | `49 49 2A 00` (little-endian) or `4D 4D 00 2A` (big-endian) |
| HEIF/HEIC | BMFF `ftyp` box with appropriate brand |
| AVIF   | BMFF `ftyp` box with `avif` / `avis` brand |
| FLAC   | `66 4C 61 43` (fLaC) |
| OGG / Opus | `4F 67 67 53` (OggS) |
| MP3    | `FF FB`, `FF F3`, `FF F2`, or ID3 tag `49 44 33` |
| WAV    | `52 49 46 46 … 57 41 56 45` (RIFF…WAVE) |
| AVI    | `52 49 46 46 … 41 56 49 20` (RIFF…AVI ) |
| WebM / MKV | EBML magic `1A 45 DF A3` |

## Format objects

Each format is a singleton `data object` that implements `MediaFormat`:

```kotlin
interface MediaFormat<DECODE_OPTS, ENCODE_OPTS> {
    val label: String
    val mimeType: String
    val extension: String
    val containerFamily: ContainerFamily
}
```

The full sets:

```kotlin
ImageFormat.all  // Jpeg, Png, Webp, Heif, Heic, Avif, Gif, Bmp, Tiff
AudioFormat.all  // Mp3, Aac, Wav, Ogg, Flac, M4a, Opus
VideoFormat.all  // Mp4, Webm, Mov, Avi, Mkv
```
