# Format Detection

Transmute provides two levels of format detection:

## Per-domain detection

Use these when you already know the media domain:

```kotlin
val imageFormat = ImageFormatDetector.detect(bytes.asBytes())
val audioFormat = AudioFormatDetector.detect(bytes.asBytes())
val videoFormat = VideoFormatDetector.detect(bytes.asBytes())
```

## Cross-domain detection

Use `Transmute.inspect().detectFormat(...)` when you need to determine the domain:

```kotlin
val format = Transmute.inspect().detectFormat(bytes.asBytes())
if (format == UnknownFormat) error("unknown format")
```

### Note on ISO-BMFF containers (`ftyp`)

Containers like MP4/MOV/M4A/HEIF/HEIC/AVIF share the same underlying container structure.
`Transmute.inspect().detectFormat(...)` handles this ambiguity explicitly to reduce cross-domain misclassification.

## Structure-level detection

`Transmute.structure.read(bytes)` (without an explicit format) integrates with the format detector:

1. Runs `inspect().detectFormat(...)` for a precise reader lookup.
2. Falls back to each registered reader's `canRead()` method (magic-byte sniffing) when the
   codec-level detector returns `UnknownFormat`.

If you already know the format, prefer the explicit overload to skip detection:

```kotlin
val wav: Wav = Transmute.structure.read(wavBytes.asBytes(), AudioFormat.Wav)
```
