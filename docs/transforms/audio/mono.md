# Mono

Mix all channels down to a single mono channel.

## Parameters

None.

## Usage

### DSL

```kotlin
Transmute.audio { mono() }.transmute(bytes.asBytes()).bytes.data
```

### Pipeline

```kotlin
transform { add(Transformers.audio().mono()) }
```

## Notes

- Stereo → mono is computed by averaging left and right channels.
- For multi-channel audio (>2), all channels are averaged equally.
- No-op if the audio is already mono.
