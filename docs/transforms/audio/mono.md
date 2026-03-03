# Audio: mono

Mix down to mono by averaging channels.

## Factory

```kotlin
Transformers.audio().mono()
```

No parameters.

## Behaviour

- Averages all input channels into a single mono channel.
- If the input is already mono, the transform is a no-op.
- Use [`channelMap`](channel-map.md) for custom channel routing (e.g. keep only the left channel).

## DSL usage

```kotlin
val transmuter = Transmute.audio.to(AudioFormat.Mp3) {
    decode {
        pipeline { mono() }
    }
}
```

## Related

- [channelMap](channel-map.md)
- [Transforms overview](README.md)
