# HEIF / HEIC

| Property | HEIF | HEIC |
|----------|------|------|
| Constant | `ImageFormat.Heif` | `ImageFormat.Heic` |
| MIME type | `image/heif` | `image/heic` |
| Extension | `heif` | `heic` |
| Container | ISOBMFF | ISOBMFF |

HEIC is the Apple variant of HEIF. Both share the same container family (`ContainerFamily.Heif`) and are handled by the same codec.

## Platform availability

| Platform | Decode | Encode |
|----------|--------|--------|
| Android  | ✓ | ✓ |
| Desktop  | plugin (GStreamer or libheif) | plugin |
| iOS      | ✓ | ✓ |

On Desktop, install the GStreamer plugin (`transmute-plugins:gstreamer`) or the self-contained libheif plugin (`transmute-plugins:libheif`).

## Encode options

```kotlin
HeifEncodeOptions(
    quality: Float = 0.80f,                 // 0.0 – 1.0
    metadataPolicy: MetadataPolicy = STRIP_ALL,
    format: ImageFormat = ImageFormat.Heif, // or ImageFormat.Heic
)
```

## Metadata support

HEIF/HEIC files may carry: `ExifMetadata`, `XmpMetadata`.

## Structure support

`HeifStructure` — ISOBMFF box list.
