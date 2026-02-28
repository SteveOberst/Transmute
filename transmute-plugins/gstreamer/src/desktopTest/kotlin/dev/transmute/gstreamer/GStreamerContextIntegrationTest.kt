package dev.transmute.gstreamer

import dev.transmute.transmute
import dev.transmute.gstreamer.GStreamerTestHelpers.requireGStreamer
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for [GStreamerPluginConfig] feature toggle DSL.
 *
 * Validates that features can be enabled/disabled individually and that
 * the plugin installs correctly through the [transmute] builder.
 */
class GStreamerContextIntegrationTest {

    @Test
    fun allFeaturesEnabledByDefault_pluginAvailable() {
        requireGStreamer {
            val transmute = transmute { plugins { install(GStreamer) } }
            val diag = transmute.diagnostics.plugin(GStreamer.key)
            assertNotNull(diag, "GStreamer diagnostics must be present")
            assertTrue(diag.current?.available == true, "GStreamer must report as available")
        }
    }

    @Test
    fun disableIndividualFeatures_doesNotThrow() {
        requireGStreamer {
            transmute {
                plugins {
                    install(GStreamer) {
                        disable(GStreamerFeature.AudioCodecs)
                        disable(GStreamerFeature.VideoCodecs)
                    }
                }
            }
            // Reaching here without exception confirms the DSL works correctly
        }
    }

    @Test
    fun setFeatureByStringId_worksForDynamicControl() {
        requireGStreamer {
            transmute {
                plugins {
                    install(GStreamer) {
                        set(GStreamerFeature.ImageEncoding.id, false)
                    }
                }
            }
            // String-based set() API must not throw
        }
    }

    @Test
    fun disableAllCodecFeatures_pluginStillInstalls() {
        requireGStreamer {
            val transmute = transmute {
                plugins {
                    install(GStreamer) {
                        disable(GStreamerFeature.AudioCodecs)
                        disable(GStreamerFeature.ImageCodecs)
                        disable(GStreamerFeature.VideoCodecs)
                    }
                }
            }
            // Plugin must still report diagnostics even when all codec features are off
            val diag = transmute.diagnostics.plugin(GStreamer.key)
            assertNotNull(diag, "Plugin diagnostics must be present even when features are disabled")
        }
    }
}
