package dev.transmute.codec

import dev.transmute.common.PipelineContext
import dev.transmute.io.TSource
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.DecodeOptions
import dev.transmute.model.core.EncodeOptions
import dev.transmute.model.core.MediaFormat

/**
 * Base decoder interface. Knows which formats it can decode.
 *
 * Format detection is handled centrally by domain-specific `FormatDetector`
 * objects (e.g. `ImageFormatDetector`, `AudioFormatDetector`, `VideoFormatDetector`),
 * not by individual decoders.
 *
 * @param F The [MediaFormat] enum this decoder operates on.
 * @param OUT The output produced by decoding.
 * @param D The [dev.transmute.common.DecodeOptions] type accepted by this decoder.
 */
interface MediaDecoder<out F : MediaFormat<*, *>, out OUT, in D : DecodeOptions> {

  /** Formats this decoder can handle. */
  val decodableFormats: Set<F>

  /**
   * Decode raw bytes into the module's intermediate representation.
   *
   * The [options] parameter carries format-specific decode hints
   * (e.g. JPEG downscale factor). Pass the domain's default options
   * when no special configuration is needed.
   */
  suspend fun decode(source: TSource, options: D, context: PipelineContext): OUT
}

/**
 * Base encoder interface.
 *
 * @param F The [MediaFormat] enum this encoder operates on.
 * @param IN The input consumed by encoding.
 * @param O The [dev.transmute.common.EncodeOptions] type accepted by this encoder.
 */
interface MediaEncoder<F : MediaFormat<*, *>, in IN, in O : EncodeOptions> {

  /** Formats this encoder can produce. */
  val encodableFormats: Set<F>

  /**
   * Encode the intermediate representation back to raw format bytes.
   *
   * The selected [format] and [options] are explicit to avoid relying on
   * mutable out-of-band state in [PipelineContext].
   */
  suspend fun encode(ir: IN, format: F, options: O, context: PipelineContext): Bytes
}

/**
 * Full codec interface that unifies decoding and encoding for a media format.
 *
 * Extends both [MediaDecoder] and [MediaEncoder], inheriting decode and encode
 * capabilities.
 *
 * @param F The [MediaFormat] enum this codec operates on (e.g. [ImageFormat]).
 * @param IR The intermediate representation produced by decoding (e.g. `ImageIR`).
 * @param D The [DecodeOptions] type accepted by this codec's decoder side.
 * @param O The [EncodeOptions] type accepted by this codec's encoder side.
 */
interface MediaCodec<F : MediaFormat<*, *>, IR, in D : DecodeOptions, in O : EncodeOptions> :
  MediaDecoder<F, IR, D>,
  MediaEncoder<F, IR, O>
