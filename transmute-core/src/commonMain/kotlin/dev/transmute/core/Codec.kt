package dev.transmute.core

/**
 * Base decoder interface. Knows which formats it can decode and can sniff
 * raw bytes to determine if they match one of its supported formats.
 *
 * @param F The [MediaFormat] enum this decoder operates on.
 * @param IR The intermediate representation produced by decoding.
 */
interface Decoder<F : MediaFormat, IR> {

  /** Formats this decoder can handle. */
  val decodableFormats: Set<F>

  /**
   * Sniff the first bytes of [data] and return the matched format,
   * or `null` if this decoder does not recognise the data.
   *
   * Implementations should be cheap (check magic bytes, not parse the whole file).
   */
  fun sniff(data: ByteArray): F?

  /** Decode raw bytes into the module's intermediate representation. */
  suspend fun decode(source: ByteArray, context: ConversionContext): IR
}

/**
 * Base encoder interface.
 *
 * @param F The [MediaFormat] enum this encoder operates on.
 * @param IR The intermediate representation consumed by encoding.
 */
interface Encoder<F : MediaFormat, IR> {

  /** Formats this encoder can produce. */
  val encodableFormats: Set<F>

  /** Encode the intermediate representation back to raw format bytes. */
  suspend fun encode(ir: IR, context: ConversionContext): ByteArray
}

/**
 * Full codec interface that unifies decoding and encoding for a media format.
 *
 * Extends both [Decoder] and [Encoder], inheriting sniff, decode, and encode
 * capabilities. This enables the format-detector system to delegate to
 * registered codecs/decoders instead of maintaining a parallel if-else chain.
 *
 * @param F The [MediaFormat] enum this codec operates on (e.g. [ImageFormat]).
 * @param IR The intermediate representation produced by decoding (e.g. `ImageIR`).
 */
interface Codec<F : MediaFormat, IR> : Decoder<F, IR>, Encoder<F, IR>

/**
 * A codec that only decodes (no encode capability).
 *
 * Provides a default [encodableFormats] of empty and a throwing [encode].
 */
interface DecoderCodec<F : MediaFormat, IR> : Codec<F, IR> {
  override val encodableFormats: Set<F> get() = emptySet()
  override suspend fun encode(ir: IR, context: ConversionContext): ByteArray =
    error("${this::class.simpleName} is decode-only")
}

/**
 * A codec that only encodes (no decode capability).
 *
 * Provides a default [decodableFormats] of empty and a throwing [decode].
 */
interface EncoderCodec<F : MediaFormat, IR> : Codec<F, IR> {
  override val decodableFormats: Set<F> get() = emptySet()
  override fun sniff(data: ByteArray): F? = null
  override suspend fun decode(source: ByteArray, context: ConversionContext): IR =
    error("${this::class.simpleName} is encode-only")
}
