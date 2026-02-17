# Rotate

Auto-rotate an image based on its EXIF orientation tag, then reset the tag to NORMAL.

## Parameters

None - rotation is derived from the embedded EXIF orientation.

## Usage

### DSL

```kotlin
Transmute.image(bytes) { rotate() }
```

### Pipeline

```kotlin
transform { add(Transformers.image().rotate()) }
```

## Notes

- Reads the EXIF orientation flag and applies the corresponding rotation/mirror.
- After transformation the orientation is set to `NORMAL` so downstream consumers see the corrected image.
- No-op if the image has no EXIF data or is already `NORMAL`.
