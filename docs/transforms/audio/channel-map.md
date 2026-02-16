# Channel Map

Remap audio channels by specifying which source channel feeds each output channel.

## Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| mapping | IntArray | — | Array where index = output channel, value = source channel index |

## Usage

### DSL

```kotlin
// Duplicate left channel to both outputs
Transmute.audio(bytes) { channelMap(intArrayOf(0, 0)) }
```

### Pipeline

```kotlin
transform { add(Transformers.audio().channelMap(intArrayOf(0, 0))) }
```

## Notes

- The length of `mapping` determines the output channel count.
- `intArrayOf(0, 0)` = duplicate left to both channels.
- `intArrayOf(1, 0)` = swap left and right channels.
- Source indices that exceed the input channel count will produce silence.
