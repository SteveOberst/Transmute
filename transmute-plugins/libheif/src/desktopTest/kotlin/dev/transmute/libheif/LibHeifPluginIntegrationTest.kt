package dev.transmute.libheif

import dev.transmute.transmute
import kotlin.test.Test

/**
 * Integration tests for the [LibHeif] plugin and [LibHeifPluginConfig] DSL.
 *
 * Validates that the plugin installs correctly through the [transmute] builder,
 * that feature-toggle DSL works without throwing, and that installation-mode
 * overrides are accepted at configuration time.
 *
 * The `desktopTest` Gradle task is gated by `TRANSMUTE_LIBHEIF_TESTS` so
 * tests may expect a working libheif installation to be present.
 */
class LibHeifPluginIntegrationTest {

    @Test
    fun install_withAllDefaults_doesNotThrow() {
        transmute {
            plugins {
                install(LibHeif)
            }
        }
        // Reaching here without exception confirms default installation works.
    }

    @Test
    fun disableImageEncoding_doesNotThrow() {
        transmute {
            plugins {
                install(LibHeif) {
                    disable(LibHeifFeature.ImageEncoding)
                }
            }
        }
    }

    @Test
    fun disableImageCodecs_doesNotThrow() {
        transmute {
            plugins {
                install(LibHeif) {
                    disable(LibHeifFeature.ImageCodecs)
                }
            }
        }
    }

    @Test
    fun setFeatureByStringId_worksForDynamicControl() {
        transmute {
            plugins {
                install(LibHeif) {
                    set(LibHeifFeature.ImageEncoding.id, false)
                }
            }
        }
        // String-based set() must not throw.
    }

    @Test
    fun disableAllFeatures_pluginStillInstalls() {
        transmute {
            plugins {
                install(LibHeif) {
                    disable(LibHeifFeature.ImageCodecs)
                    disable(LibHeifFeature.ImageEncoding)
                }
            }
        }
    }

    @Test
    fun useSystemInstallation_doesNotThrow() {
        transmute {
            plugins {
                install(LibHeif) {
                    useSystemInstallation()
                }
            }
        }
    }

    @Test
    fun timeout_configurationIsAccepted() {
        transmute {
            plugins {
                install(LibHeif) {
                    timeout(60_000L)
                }
            }
        }
    }

    @Test
    fun multipleInstallsInSequence_doNotInterfere() {
        // Each build creates an isolated resolver state through the config lifecycle.
        transmute { plugins { install(LibHeif) } }
        transmute { plugins { install(LibHeif) { disable(LibHeifFeature.ImageEncoding) } } }
        transmute { plugins { install(LibHeif) { useSystemInstallation() } } }
    }
}
