# Format Detection

Transmute provides two levels of format detection:

## Per-domain detection

Use these when you already know the media domain:

```kotlin
import dev.transmute.image.ImageFormatDetector
import dev.transmute.audio.AudioFormatDetector
import dev.transmute.video.VideoFormatDetector

val imageFormat = ImageFormatDetector.detect(bytes)
val audioFormat = AudioFormatDetector.detect(bytes)
val videoFormat = VideoFormatDetector.detect(bytes)
```

## Cross-domain detection

Use `Transmute.detectFormat(...)` when you need to determine the domain:

```kotlin
import dev.transmute.Transmute
import dev.transmute.core.UnknownFormat

val format = Transmute.detectFormat(bytes)
if (format == UnknownFormat) error("unknown format")
```

### Note on ISO-BMFF containers (`ftyp`)

Containers like MP4/MOV/M4A/HEIF/HEIC/AVIF share the same underlying container structure.
`Transmute.detectFormat(...)` handles this ambiguity explicitly to reduce cross-domain misclassification.

