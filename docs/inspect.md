# `Transmute.inspect()`

`Transmute.inspect()` is the decode-less API for **format detection**, **container/header metadata**, and **lightweight probing** (e.g. codecs inside MP4/MOV).

## Example: extract a thumbnail (first frame)

```kotlin
// imports omitted

suspend fun extract(videoBytes: ByteArray): ByteArray {
  val inspect = Transmute.inspect()

  val out = inspect.video.thumbnailFirstFrame(videoBytes.asBytes(), imageEncodeOptions = PngEncodeOptions())
  return out.bytes.data
}
```

## Cross-domain format detection

```kotlin
// imports omitted

fun detect(bytes: ByteArray) {
  val format = Transmute.inspect().detectFormat(bytes.asBytes())
  if (format == dev.transmute.core.UnknownFormat) error("unknown format")
}
```

## Structure reading

For deep container/header inspection — parsing the full structural layout of a file without decoding pixel or sample data — use `Transmute.structure`:

```kotlin
val png: Png = Transmute.structure.read(pngBytes.asBytes(), ImageFormat.Png)
```

See `docs/structures.md` for the full API.
