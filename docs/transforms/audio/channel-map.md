# Audio: channelMap

Remap audio channels to a different layout.

## Factory

```kotlin
Transformers.audio().channelMap(mapping: IntArray)
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `mapping` | `IntArray` | ✅ | Output-to-source channel index mapping |

## Behaviour

- The output has `mapping.size` channels.
- `mapping[i]` is the index of the source channel to copy into output channel `i`.
- Source channel indices are 0-based: `0` = left, `1` = right for stereo input.
- Repeated source indices are allowed (e.g. duplicate a channel).

## Examples

```kotlin
// Stereo → mono left channel only
Transformers.audio().channelMap(intArrayOf(0))

// Stereo → mono right channel only
Transformers.audio().channelMap(intArrayOf(1))

// Swap left and right
Transformers.audio().channelMap(intArrayOf(1, 0))

// Stereo → both channels carry left
Transformers.audio().channelMap(intArrayOf(0, 0))
```

## DSL usage

```kotlin
val transmuter = Transmute.audio.to(AudioFormat.Wav) {
    decode {
        pipeline {
            channelMap(intArrayOf(0))  // extract left channel
        }
    }
}
```

## Related

- [mono](mono.md)
- [Transforms overview](README.md)
