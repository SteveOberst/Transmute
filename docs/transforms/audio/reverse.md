# Reverse

Reverse the playback direction of audio.

## Parameters

None.

## Usage

### DSL

```kotlin
Transmute.audio(bytes) { reverse() }
```

### Pipeline

```kotlin
transform { add(Transformers.audio().reverse()) }
```

## Notes

- Reverses sample order within each channel independently.
- Duration and sample rate remain unchanged.
