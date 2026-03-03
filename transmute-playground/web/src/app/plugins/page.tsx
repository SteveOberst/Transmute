'use client'

import { useEffect, useState, useCallback } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import Navbar from '@/components/Navbar'
import { fetchPlugins, updatePlugin } from '@/lib/api'
import { useToast } from '@/components/Toast'
import type { PluginDescriptor, FeatureDescriptor } from '@/lib/types'

export default function PluginsPage() {
  const [plugins, setPlugins] = useState<PluginDescriptor[]>([])
  const [loaded, setLoaded] = useState(false)
  const [expandedKey, setExpandedKey] = useState<string | null>(null)
  const [updating, setUpdating] = useState<string | null>(null)
  const toast = useToast()

  useEffect(() => {
    fetchPlugins()
      .then((data) => {
        setPlugins(data)
        setLoaded(true)
      })
      .catch(() => setLoaded(true))
  }, [])

  const toggleFeature = useCallback(
    async (pluginKey: string, featureId: string, enabled: boolean) => {
      setUpdating(featureId)
      try {
        const updated = await updatePlugin(pluginKey, {
          features: { [featureId]: enabled },
        })
        setPlugins((prev) =>
          prev.map((p) => (p.key === pluginKey ? updated : p)),
        )
      } catch (e: unknown) {
        toast.error(e instanceof Error ? e.message : 'Feature update failed')
      } finally {
        setUpdating(null)
      }
    },
    [],
  )

  const togglePlugin = useCallback(
    async (pluginKey: string, enabled: boolean) => {
      setUpdating(pluginKey)
      try {
        const updated = await updatePlugin(pluginKey, { enabled })
        setPlugins((prev) =>
          prev.map((p) => (p.key === pluginKey ? updated : p)),
        )
      } catch (e: unknown) {
        toast.error(e instanceof Error ? e.message : 'Plugin update failed')
      } finally {
        setUpdating(null)
      }
    },
    [],
  )

  return (
    <div className="min-h-dvh">
      <Navbar />

      <main className="max-w-4xl mx-auto px-6 py-10">
        {/* Header */}
        <div className="mb-10">
          <motion.h1
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            className="font-display text-4xl sm:text-5xl text-white tracking-tight"
          >
            Plugins
          </motion.h1>
          <motion.p
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.05 }}
            className="text-[#666680] text-sm mt-2 max-w-lg"
          >
            Manage installed Transmute plugins and their features.
            Toggling features will rebuild the internal Transmute instance.
          </motion.p>
        </div>

        {!loaded ? (
          <div className="flex justify-center py-20">
            <div className="w-6 h-6 border-2 border-[var(--accent)] border-t-transparent rounded-full animate-spin" />
          </div>
        ) : plugins.length === 0 ? (
          <div className="text-center py-20 text-[#555566] font-mono text-sm">
            No plugins installed
          </div>
        ) : (
          <div className="space-y-4">
            {plugins.map((plugin, idx) => {
              const expanded = expandedKey === plugin.key
              return (
                <motion.div
                  key={plugin.key}
                  initial={{ opacity: 0, y: 12 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: idx * 0.05 }}
                  className="
                    rounded-2xl border border-[var(--surface-3)]
                    bg-[var(--surface-1)] overflow-hidden
                    transition-colors hover:border-[var(--surface-3)]/80
                  "
                >
                  {/* Plugin header */}
                  <button
                    onClick={() => setExpandedKey(expanded ? null : plugin.key)}
                    className="
                      w-full px-6 py-5 flex items-center justify-between
                      text-left group
                    "
                  >
                    <div className="flex items-center gap-4">
                      {/* Icon */}
                      <div className="
                        w-10 h-10 rounded-xl flex items-center justify-center
                        bg-[var(--accent)]/8 text-[var(--accent)] text-lg font-display
                        group-hover:glow-accent-sm transition-shadow
                      ">
                        {plugin.name.charAt(0)}
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="text-white font-medium">
                            {plugin.name}
                          </span>
                          {plugin.version && (
                            <span className="text-[10px] font-mono text-[#555566]">
                              v{plugin.version}
                            </span>
                          )}
                          {plugin.status && !plugin.status.available && (
                            <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-amber-500/10 text-amber-400">
                              Unavailable
                            </span>
                          )}
                        </div>
                        <p className="text-xs text-[#666680] mt-0.5">
                          {plugin.description}
                        </p>
                      </div>
                    </div>

                    <div className="flex items-center gap-4">
                      {/* Domain chips */}
                      <div className="hidden sm:flex gap-1.5">
                        {plugin.domains.map((d) => (
                          <span
                            key={d}
                            className={`text-[10px] font-mono px-2 py-0.5 rounded ${
                              d === 'IMAGE'
                                ? 'chip-image'
                                : d === 'AUDIO'
                                  ? 'chip-audio'
                                  : 'chip-video'
                            }`}
                          >
                            {d}
                          </span>
                        ))}
                      </div>

                      {/* Enable/disable */}
                      <div onClick={(e) => e.stopPropagation()}>
                        <button
                          onClick={() => togglePlugin(plugin.key, !plugin.enabled)}
                          disabled={updating === plugin.key}
                          aria-checked={plugin.enabled}
                          role="switch"
                            className={`relative w-9 h-5 rounded-full overflow-hidden transition-colors disabled:opacity-50 ${
                              plugin.enabled ? 'bg-[var(--accent)]' : 'bg-[var(--surface-3)]'
                            }`}
                          >
                            <span
                              className={`absolute left-0 top-0.5 w-4 h-4 rounded-full bg-white shadow transition-transform ${
                                plugin.enabled ? 'translate-x-[18px]' : 'translate-x-0.5'
                            }`}
                          />
                        </button>
                      </div>

                      {/* Expand icon */}
                      <motion.span
                        animate={{ rotate: expanded ? 180 : 0 }}
                        className="text-[#555566] text-sm"
                      >
                        ▾
                      </motion.span>
                    </div>
                  </button>

                  {/* Features list */}
                  <AnimatePresence>
                    {expanded && (
                      <motion.div
                        initial={{ height: 0, opacity: 0 }}
                        animate={{ height: 'auto', opacity: 1 }}
                        exit={{ height: 0, opacity: 0 }}
                        transition={{ duration: 0.2 }}
                        className="overflow-hidden"
                      >
                        <div className="
                          px-6 pb-5 border-t border-[var(--surface-3)]/50
                          pt-4 space-y-3
                        ">
                          {plugin.features.length === 0 ? (
                            <p className="text-xs text-[#555566] font-mono">
                              No configurable features
                            </p>
                          ) : (
                            plugin.features.map((feat: FeatureDescriptor) => (
                              <div
                                key={feat.id}
                                className="
                                  flex items-center justify-between py-2 px-4
                                  rounded-xl bg-[var(--surface-2)]/50
                                "
                              >
                                <div>
                                  <span className="text-sm text-white">
                                    {feat.name}
                                  </span>
                                  <p className="text-xs text-[#555566] mt-0.5">
                                    {feat.description}
                                  </p>
                                </div>
                                <button
                                  onClick={() => toggleFeature(plugin.key, feat.id, !feat.currentlyEnabled)}
                                  disabled={!plugin.enabled || updating === feat.id}
                                  aria-checked={feat.currentlyEnabled}
                                  role="switch"
                                    className={`relative w-9 h-5 rounded-full overflow-hidden transition-colors disabled:opacity-40 ${
                                      feat.currentlyEnabled ? 'bg-[var(--accent)]' : 'bg-[var(--surface-3)]'
                                    }`}
                                  >
                                    <span
                                      className={`absolute left-0 top-0.5 w-4 h-4 rounded-full bg-white shadow transition-transform ${
                                        feat.currentlyEnabled ? 'translate-x-[18px]' : 'translate-x-0.5'
                                    }`}
                                  />
                                </button>
                              </div>
                            ))
                          )}

                          {/* Added formats */}
                          {plugin.addedFormats.length > 0 && (
                            <div className="mt-4">
                              <div className="text-[10px] font-mono text-[#555566] uppercase tracking-wider mb-2">
                                Registered Formats
                              </div>
                              <div className="flex flex-wrap gap-1.5">
                                {plugin.addedFormats.map((f) => (
                                  <span
                                    key={f}
                                    className="
                                      px-2 py-0.5 rounded text-[10px] font-mono
                                      bg-[var(--surface-3)] text-[#888898]
                                    "
                                  >
                                    {f}
                                  </span>
                                ))}
                              </div>
                            </div>
                          )}
                        </div>
                      </motion.div>
                    )}
                  </AnimatePresence>
                </motion.div>
              )
            })}
          </div>
        )}
      </main>
    </div>
  )
}
