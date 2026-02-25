package dev.transmute.codec.pipeline

/**
 * Ordered, mutable pipeline of [Transform] steps with
 * positional insertion helpers.
 *
 * Transforms execute in list order during conversion.  The pipeline
 * is populated either by convenience methods on a transmuter (e.g.
 * `scale()`, `trim()`) or by the `transform { }` DSL block which
 * gives full positional control:
 *
 * ```kotlin
 * Transmute.video {
 *   transform {
 *     add(Transformers.video().resize(640, 480))
 *     before<VideoResizeTransform>(Transformers.video().trim(0, 5000))
 *   }
 * }.transmute(buf)
 * ```
 */
class TransformPipeline<IR> {

  @PublishedApi
  internal val _transforms = mutableListOf<Transform<IR>>()

  /** Snapshot of the current transform list (in execution order). */
  val transforms: List<Transform<IR>> get() = _transforms.toList()

  /** Number of transforms currently in the pipeline. */
  val size: Int get() = _transforms.size

  /** `true` when the pipeline contains no transforms. */
  val isEmpty: Boolean get() = _transforms.isEmpty()

  // -- Append / prepend --

  /** Append [transform] at the end of the pipeline. */
  fun add(transform: Transform<IR>): TransformPipeline<IR> = apply { _transforms.add(transform) }

  /** Alias for [add]. */
  fun addLast(transform: Transform<IR>): TransformPipeline<IR> = add(transform)

  /** Prepend [transform] at the very beginning of the pipeline. */
  fun addFirst(transform: Transform<IR>): TransformPipeline<IR> = apply { _transforms.add(0, transform) }

  /** Append all [transforms] at the end. */
  fun addAll(transforms: Iterable<Transform<IR>>): TransformPipeline<IR> = apply {
    _transforms.addAll(transforms)
  }

  // -- Positional insertion --

  /**
   * Insert [transform] **before** the first occurrence of type [B].
   *
   * ```kotlin
   * pipeline.before<VideoResizeTransform>(myTrimTransform)
   * ```
   *
   * @throws IllegalStateException if no transform of type [B] exists.
   */
  inline fun <reified B : Transform<IR>> before(transform: Transform<IR>): TransformPipeline<IR> = apply {
    val index = _transforms.indexOfFirst { it is B }
    require(index >= 0) { "No transform of type ${B::class.simpleName} in pipeline" }
    _transforms.add(index, transform)
  }

  /** Alias for [before]. */
  inline fun <reified B : Transform<IR>> addBefore(transform: Transform<IR>): TransformPipeline<IR> =
    before<B>(transform)

  /**
   * Insert [transform] **after** the first occurrence of type [B].
   *
   * @throws IllegalStateException if no transform of type [B] exists.
   */
  inline fun <reified B : Transform<IR>> after(transform: Transform<IR>): TransformPipeline<IR> = apply {
    val index = _transforms.indexOfFirst { it is B }
    require(index >= 0) { "No transform of type ${B::class.simpleName} in pipeline" }
    _transforms.add(index + 1, transform)
  }

  /** Alias for [after]. */
  inline fun <reified B : Transform<IR>> addAfter(transform: Transform<IR>): TransformPipeline<IR> =
    after<B>(transform)

  // -- Removal --

  /** Remove the first occurrence of type [B]. Returns `true` if found. */
  inline fun <reified B : Transform<IR>> remove(): Boolean {
    val index = _transforms.indexOfFirst { it is B }
    if (index >= 0) { _transforms.removeAt(index); return true }
    return false
  }

  /** Remove a specific transform instance. */
  fun remove(transform: Transform<IR>): Boolean = _transforms.remove(transform)

  /** Remove all transforms. */
  fun clear(): TransformPipeline<IR> = apply { _transforms.clear() }

  // -- Replacement --

  /**
   * Replace the first occurrence of type [B] with [replacement].
   *
   * @throws IllegalStateException if no transform of type [B] exists.
   */
  inline fun <reified B : Transform<IR>> replace(replacement: Transform<IR>): TransformPipeline<IR> = apply {
    val index = _transforms.indexOfFirst { it is B }
    require(index >= 0) { "No transform of type ${B::class.simpleName} in pipeline" }
    _transforms[index] = replacement
  }

  // -- Queries --

  /** `true` if the pipeline contains at least one transform of type [B]. */
  inline fun <reified B : Transform<IR>> has(): Boolean = _transforms.any { it is B }

  /** Return the first transform of type [B], or `null`. */
  inline fun <reified B : Transform<IR>> get(): B? = _transforms.firstOrNull { it is B } as? B

  // -- Kotlin operators --

  operator fun plusAssign(transform: Transform<IR>) { add(transform) }
  operator fun iterator(): Iterator<Transform<IR>> = _transforms.iterator()

  override fun toString(): String =
    "TransformPipeline(${_transforms.joinToString { it.id.value }})"
}
