package dev.transmute.core.pipeline

/**
 * A decode pipeline: some IN in, IR out.
 *
 * The pipeline may contain arbitrary typed steps; the only constraint is that
 * the *final* output type is the IR you want.
 */
typealias DecodePipeline<IN, IR> = Pipeline<IN, IR>
