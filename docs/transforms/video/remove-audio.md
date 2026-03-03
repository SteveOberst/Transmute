# Video: removeAudio

Strip the audio track from a video.

## Factory

```kotlin
Transformers.video().removeAudio()
```

No parameters.

## Behaviour

- Removes all audio streams from the output container.
- The video stream is unchanged.
- If the input has no audio track, the transform is a no-op.

## DSL usage

```kotlin
val transmuter = Transmute.video.to(VideoFormat.Mp4) {
    decode {
        pipeline { removeAudio() }
    }
}
```

## Related

- [trim](trim.md)
- [Transforms overview](README.md)
