package dev.transmute.core

/**
 * Base decoder interface. Knows which formats it can decode and can sniff
 * raw bytes to determine if they match one of its supported formats.
 *
 * @param F The [MediaFormat] enum this decoder operates on.
 * @param IR The intermediate representation produced by decoding.
 * @param D The [DecodeOptions] type accepted by this decoder.
 */
interface Decoder<out F : MediaFormat<*, *>, out IR, in D : DecodeOptions> {

  /** Formats this decoder can handle. */
  val decodableFormats: Set<F>

  /**
   * Sniff the first bytes of [data] and return the matched format,
   * or `null` if this decoder does not recognise the data.
   *
   * Implementations should be cheap (check magic bytes, not parse the whole file).
   */
  fun sniff(data: Bytes): F?

  /**
   * Decode raw bytes into the module's intermediate representation.
   *
   * The [options] parameter carries format-specific decode hints
   * (e.g. JPEG downscale factor). Pass the domain's default options
   * when no special configuration is needed.
   */
  suspend fun decode(source: Bytes, options: D, context: TransmuteContext): IR
}

/**
 * Base encoder interface.
 *
 * @param F The [MediaFormat] enum this encoder operates on.
 * @param IR The intermediate representation consumed by encoding.
 * @param O The [EncodeOptions] type accepted by this encoder.
 */
interface Encoder<F : MediaFormat<*, *>, in IR, in O : EncodeOptions> {

  /** Formats this encoder can produce. */
  val encodableFormats: Set<F>

  /**
   * Encode the intermediate representation back to raw format bytes.
   *
   * The selected [format] and [options] are explicit to avoid relying on
   * mutable out-of-band state in [TransmuteContext].
   */
  suspend fun encode(ir: IR, format: F, options: O, context: TransmuteContext): Bytes
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
 * @param D The [DecodeOptions] type accepted by this codec's decoder side.
 * @param O The [EncodeOptions] type accepted by this codec's encoder side.
 */
interface Codec<F : MediaFormat<*, *>, IR, in D : DecodeOptions, in O : EncodeOptions> : Decoder<F, IR, D>, Encoder<F, IR, O>
