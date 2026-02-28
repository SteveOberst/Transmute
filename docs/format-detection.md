# Format Detection

Transmute provides multiple levels of format detection, from quick magic-byte sniffing to full structural probing.

## Cross-domain detection

Use `Transmute.inspect.detectFormat(...)` when you don't know the media domain:

```kotlin
val format = Transmute.inspect.detectFormat(bytes.asBytes())
when (format) {
    is ImageFormat -> println("Image: $format")
    is AudioFormat -> println("Audio: $format")
    is VideoFormat -> println("Video: $format")
    UnknownFormat  -> println("Unknown format")
}
```

## Per-domain detection

Use these when you already know the media domain:

```kotlin
val imageFormat = ImageFormatDetector.detect(bytes.asBytes())
val audioFormat = AudioFormatDetector.detect(bytes.asBytes())
val videoFormat = VideoFormatDetector.detect(bytes.asBytes())
```

### Note on ISO-BMFF containers (`ftyp`)

Containers like MP4/MOV/M4A/HEIF/HEIC/AVIF share the same underlying container structure.
`Transmute.inspect.detectFormat(...)` handles this ambiguity explicitly to reduce cross-domain misclassification.

## Structure-level detection

`Transmute.structure.read(bytes)` (without an explicit format) integrates with the format detector:

1. Runs `inspect.detectFormat(...)` for a precise reader lookup.
2. Falls back to each registered reader's `canRead()` method (magic-byte sniffing) when the
   codec-level detector returns `UnknownFormat`.

If you already know the format, prefer the explicit overload to skip detection:

```kotlin
val wav: Wav = Transmute.structure.read(wavBytes.asBytes(), AudioFormat.Wav)
```

### Lambda sugar

```kotlin
val channels = Transmute.structure.read<Wav>(wavBytes.asBytes(), AudioFormat.Wav) {
    fmt.numChannels
}
```

### From a TSource (suspending)

Auto-detection works with `TSource` too — the bytes are read asynchronously before
probing:

```kotlin
suspend fun detect(source: TSource): MediaStructure =
    Transmute.structure.read(source)
```

See [structures.md](structures.md) for the full structure API, including
IO abstractions (`TSource`, `TSink`, `TChannel`).
