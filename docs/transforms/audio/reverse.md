# Audio: reverse

Reverse the audio playback direction.

## Factory

```kotlin
Transformers.audio().reverse()
```

No parameters.

## Behaviour

- The entire sample buffer is reversed in place.
- Duration and format are unchanged.

## DSL usage

```kotlin
val transmuter = Transmute.audio.to(AudioFormat.Wav) {
    decode {
        pipeline { reverse() }
    }
}
```

## Related

- [trim](trim.md)
- [speed](speed.md)
- [Transforms overview](README.md)
