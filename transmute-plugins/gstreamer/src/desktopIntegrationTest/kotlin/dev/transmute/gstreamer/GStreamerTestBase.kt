package dev.transmute.gstreamer

import dev.transmute.image.HeifEncodeOptions
import dev.transmute.image.ImageFormat
import kotlin.test.BeforeTest
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue

/**
 * Base class for GStreamer integration tests.
 *
 * Skips all tests via JUnit 5 [Assumptions.assumeTrue] when GStreamer is
 * not installed on the current machine.  This replaces the task-level
 * `onlyIf` gate that was previously used in `build.gradle.kts`.
 *
 * Subclasses continue to carry the JVM `@Test` / `@BeforeTest` annotations
 * from kotlin.test; they do not need to import JUnit 5 directly.
 */
abstract class GStreamerTestBase {

  companion object {
    @Volatile
    private var cachedHeifEncodeSupport: Boolean? = null

    @Volatile
    private var cachedAvifEncodeSupport: Boolean? = null
  }

  @BeforeTest
  fun assumeGStreamerAvailable() {
    assumeTrue(
      "GStreamer is not available on this machine -- skipping test",
      GStreamerResolver.available,
    )
  }

  protected fun assumeLegacyAviEncodeSupported() {
    assumeTrue(
      "Legacy AVI encode support is not available on this GStreamer runtime -- skipping test",
      GStreamerResolver.hasElement("avenc_mpeg4") && GStreamerResolver.hasElement("avimux"),
    )
  }

  protected fun assumeHeifImageEncodeSupported() {
    assumeTrue(
      "GStreamer HEIF/HEIC image encode is not available on this runtime -- skipping test",
      heifImageEncodeSupported(),
    )
  }

  protected fun assumeAvifImageEncodeSupported() {
    assumeTrue(
      "GStreamer AVIF image encode is not available on this runtime -- skipping test",
      avifImageEncodeSupported(),
    )
  }

  private fun heifImageEncodeSupported(): Boolean {
    cachedHeifEncodeSupport?.let { return it }
    val supported =
      GStreamerResolver.hasElement("x265enc") &&
        GStreamerResolver.hasElement("h265parse") &&
        GStreamerResolver.hasElement("mp4mux") &&
        runCatching {
          runBlocking {
            GstImageEncoder().encode(
              GStreamerTestHelpers.solidColor(8, 8, r = 1, g = 2, b = 3),
              ImageFormat.Heif,
              HeifEncodeOptions(),
              GStreamerTestHelpers.testContext(),
            )
          }
        }.isSuccess
    cachedHeifEncodeSupport = supported
    return supported
  }

  private fun avifImageEncodeSupported(): Boolean {
    cachedAvifEncodeSupport?.let { return it }
    val supported =
      GStreamerResolver.hasElement("av1enc") &&
        GStreamerResolver.hasElement("isofmp4mux") &&
        runCatching {
          runBlocking {
            GstImageEncoder().encode(
              GStreamerTestHelpers.solidColor(8, 8, r = 1, g = 2, b = 3),
              ImageFormat.Avif,
              HeifEncodeOptions(format = ImageFormat.Avif),
              GStreamerTestHelpers.testContext(),
            )
          }
        }.isSuccess
    cachedAvifEncodeSupport = supported
    return supported
  }
}
