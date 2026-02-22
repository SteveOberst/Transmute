# Remove Audio

Strip the audio track from a video.

## Parameters

None.

## Usage

### DSL

```kotlin
Transmute.video { removeAudio() }.transmute(bytes).bytes
```

### Pipeline

```kotlin
transform { add(Transformers.video().removeAudio()) }
```

## Notes

- The video track is kept intact; only the audio track is removed.
- No-op if the video has no audio track.
- Useful for creating silent loops or reducing file size.
