# Reverse

Reverse the playback direction of audio.

## Parameters

None.

## Usage

### DSL

```kotlin
import dev.transmute.core.asBytes
Transmute.audio { reverse() }.transmute(bytes.asBytes()).bytes.data
```

### Pipeline

```kotlin
transform { add(Transformers.audio().reverse()) }
```

## Notes

- Reverses sample order within each channel independently.
- Duration and sample rate remain unchanged.
