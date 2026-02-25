package dev.transmute.gstreamer

import dev.transmute.common.PrintLogger
import dev.transmute.common.TransmuteContext
import dev.transmute.gstreamer.GStreamerTestHelpers.requireGStreamer
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for the [TransmuteContext.Builder.gstreamer] DSL.
 *
 * Validates that the GStreamer extension correctly installs codecs into
 * the context's registries (audio, video, image) when GStreamer is present.
 */
class GStreamerContextIntegrationTest {

    @Test
    fun gstreamerConfig_storesInContext() {
        requireGStreamer {
            val ctx = TransmuteContext {
                logger = PrintLogger
                gstreamer {
                    audio = true
                    video = true
                    image = true
                }
            }
            val cfg = ctx.gstreamerConfig
            assertNotNull(cfg, "GStreamer config must be stored in context")
            assertTrue(cfg.audio, "Audio must be enabled")
            assertTrue(cfg.video, "Video must be enabled")
            assertTrue(cfg.image, "Image must be enabled")
        }
    }

    @Test
    fun gstreamerConfig_selectiveDomains() {
        requireGStreamer {
            val ctx = TransmuteContext {
                logger = PrintLogger
                gstreamer {
                    audio = true
                    video = false
                    image = false
                }
            }
            val cfg = ctx.gstreamerConfig
            assertNotNull(cfg)
            assertTrue(cfg.audio)
            assertTrue(!cfg.video)
            assertTrue(!cfg.image)
        }
    }
}
