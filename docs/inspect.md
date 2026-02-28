# `Transmute.inspect`

`Transmute.inspect` is the decode-less API for **format detection**, **container/header metadata**, and **lightweight probing** (e.g. codecs inside MP4/MOV, video thumbnails).

## Cross-domain format detection

```kotlin
val format = Transmute.inspect.detectFormat(bytes.asBytes())
if (format == UnknownFormat) error("unknown format")

when (format) {
    is ImageFormat -> println("Image: $format")
    is AudioFormat -> println("Audio: $format")
    is VideoFormat -> println("Video: $format")
}
```

## Per-domain detection

When you already know the media domain:

```kotlin
val imageFormat = ImageFormatDetector.detect(bytes.asBytes())
val audioFormat = AudioFormatDetector.detect(bytes.asBytes())
val videoFormat = VideoFormatDetector.detect(bytes.asBytes())
```

## Video thumbnail extraction

Extract the first frame of a video as an image without full decode:

```kotlin
suspend fun extract(videoBytes: ByteArray): ByteArray {
    val out = Transmute.inspect.video.thumbnailFirstFrame(
        videoBytes.asBytes(),
        imageEncodeOptions = PngEncodeOptions(),
    )
    return out.bytes.data
}
```

## Structure reading

For deep container/header inspection — parsing the full structural layout of a file without decoding pixel or sample data — use `Transmute.structure`:

```kotlin
val png: Png = Transmute.structure.read(pngBytes.asBytes(), ImageFormat.Png)
```

### Lambda sugar

Extract individual fields without a local variable:

```kotlin
val width: Int = Transmute.structure.read<Png>(pngBytes.asBytes(), ImageFormat.Png) {
    ihdr.width.toInt()
}
```

### Suspending reads from a TSource

When the data comes from a file, network, or any `TSource`:

```kotlin
suspend fun inspectPng(source: TSource) {
    val png: Png = Transmute.structure.read(source, ImageFormat.Png)
    println("${png.ihdr.width} × ${png.ihdr.height}")
}
```

See [structures.md](structures.md) for the full structure API, including
IO abstractions (`TSource`, `TSink`, `TChannel`).
