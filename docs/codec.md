# Codec

`Transmute.codec` gives direct, one-shot access to decode, encode, format detection, structure decoding, and metadata decoding — without building a transmuter or running a full pipeline.

## ImageCodec / AudioCodec / VideoCodec

Each domain codec is accessible as a property:

```kotlin
Transmute.codec.image  // ImageCodec
Transmute.codec.audio  // AudioCodec
Transmute.codec.video  // VideoCodec
```

Each codec can:
- Detect format from bytes
- Decode bytes to an IR
- Encode an IR to bytes
- Return the default decoder/encoder for that domain

```kotlin
// Format detection
val format: ImageFormat = Transmute.codec.image.detectFormat(bytes)
val format: AudioFormat = Transmute.codec.audio.detectFormat(bytes)
val format: VideoFormat = Transmute.codec.video.detectFormat(bytes)

// Decode
val decoded: Decoded<ImageFormat, ImageIR> = Transmute.codec.image.decode(bytes)
val decoded: Decoded<AudioFormat, AudioIR> = Transmute.codec.audio.decode(bytes)

// Encode
val encoded: EncodedBytes<ImageFormat> = Transmute.codec.image.encode(decoded, CanonicalImageEncodeOptions())
```

## Structure decoding

```kotlin
// Decode to high-level typed structure
val structure: MediaStructure = Transmute.codec.decodeStructure(bytes, ImageFormat.Png)
val structure: MediaStructure = Transmute.codec.decodeStructure(source, AudioFormat.Flac)

// Decode to low-level binary structure
val raw: RawMediaStructure = Transmute.codec.decodeRawStructure(bytes, VideoFormat.Mp4)

// Check support
val hasStructure: Boolean = Transmute.codec.hasStructureDecoder(ImageFormat.Heif)
val hasRaw: Boolean      = Transmute.codec.hasRawStructureDecoder(AudioFormat.Mp3)
```

## Metadata decoding

```kotlin
// Decode all metadata blocks
val metadata: List<MediaMetadata> = Transmute.codec.decodeMetadata(bytes, ImageFormat.Jpeg)
val metadata = Transmute.codec.decodeMetadata(source, AudioFormat.Mp3)

// Returns empty list if no metadata decoder is registered (does not throw)
val metadata = Transmute.codec.decodeMetadata(bytes, ImageFormat.Gif) // → emptyList()

// Check support
val hasMetadata: Boolean = Transmute.codec.hasMetadataDecoder(format)
```

## One-shot dispatch

For simple re-encoding without transforms or format selection:

```kotlin
// Dispatch on TransmuteType — uses default decode/encode pipeline
val resultBytes: ByteArray = Transmute.transmute(TransmuteType.Image, inputBytes)
val resultBytes: ByteArray = Transmute.transmute(TransmuteType.Audio, inputBytes)
val resultBytes: ByteArray = Transmute.transmute(TransmuteType.Video, inputBytes)
```

This is the quickest path but provides no control over output format or transforms. Use the transmuter builders (`Transmute.image { }`) for anything more complex.

## EncodedBytes

The result type of encode operations:

```kotlin
data class EncodedBytes<F : MediaFormat<*, *>>(
    val format: F,    // the format that was actually written
    val bytes: Bytes, // the encoded data
)

val encoded: EncodedBytes<ImageFormat.Jpeg> = ...
val rawBytes: ByteArray = encoded.bytes.data
val format: ImageFormat.Jpeg = encoded.format
```

For dynamic-output transmuters the format is `ImageFormat` (the erased supertype):

```kotlin
val encoded: EncodedBytes<ImageFormat> = Transmute.image { }.transmute(source)
when (encoded.format) {
    is ImageFormat.Jpeg -> handleJpeg(encoded.bytes.data)
    is ImageFormat.Png  -> handlePng(encoded.bytes.data)
    else -> {}
}
```
