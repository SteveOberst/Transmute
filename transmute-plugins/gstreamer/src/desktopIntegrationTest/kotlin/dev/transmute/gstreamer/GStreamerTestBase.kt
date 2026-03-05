package dev.transmute.gstreamer

import kotlin.test.BeforeTest
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

  @BeforeTest
  fun assumeGStreamerAvailable() {
      assumeTrue(
        "GStreamer is not available on this machine -- skipping test",
        GStreamerResolver.available,
      )
  }
}
