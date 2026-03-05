package dev.transmute.filesystem

import dev.transmute.common.TransmuteContext
import dev.transmute.io.TChannel
import dev.transmute.io.TSink
import dev.transmute.io.TSource
import dev.transmute.io.channel
import dev.transmute.io.sink
import dev.transmute.io.source

/** Create a [TSource] for this path using [fs]. */
fun TPath.asSource(fs: TransmuteFileSystem): TSource = fs.source(this)

/** Create a [TSink] for this path using [fs]. */
fun TPath.asSink(fs: TransmuteFileSystem, mode: WriteMode = WriteMode.Overwrite): TSink = fs.sink(this, mode)

/** Create a [TChannel] for this path using [fs]. */
fun TPath.asChannel(fs: TransmuteFileSystem, mode: WriteMode = WriteMode.Overwrite): TChannel = fs.channel(this, mode)

/** Create a [TSource] for this path using [ctx.fileSystem]. */
fun TPath.asSource(ctx: TransmuteContext): TSource {
  val fs = ctx.fileSystem ?: error("No TransmuteFileSystem configured on this TransmuteContext")
  return fs.source(this)
}

/** Create a [TSink] for this path using [ctx.fileSystem]. */
fun TPath.asSink(ctx: TransmuteContext, mode: WriteMode = WriteMode.Overwrite): TSink {
  val fs = ctx.fileSystem ?: error("No TransmuteFileSystem configured on this TransmuteContext")
  return fs.sink(this, mode)
}

/** Create a [TChannel] for this path using [ctx.fileSystem]. */
fun TPath.asChannel(ctx: TransmuteContext, mode: WriteMode = WriteMode.Overwrite): TChannel {
  val fs = ctx.fileSystem ?: error("No TransmuteFileSystem configured on this TransmuteContext")
  return fs.channel(this, mode)
}
