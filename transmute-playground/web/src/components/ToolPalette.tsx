'use client'

import { motion } from 'framer-motion'
import type { TransformInfo, MediaDomain } from '@/lib/types'
import type { HistoryEntry } from '@/lib/editorTypes'

interface Props {
  transforms: TransformInfo[]
  domain: MediaDomain | null
  activeTool: TransformInfo | null
  history: HistoryEntry[]
  onSelect: (tool: TransformInfo) => void
}

const DOMAIN_ORDER: MediaDomain[] = ['IMAGE', 'AUDIO', 'VIDEO']
const ALL_DOMAINS: string[] = [...DOMAIN_ORDER, 'UNIVERSAL']
const DOMAIN_LABELS: Record<string, string> = {
  IMAGE: 'Image',
  AUDIO: 'Audio',
  VIDEO: 'Video',
  UNIVERSAL: 'Universal',
}

export default function ToolPalette({ transforms, domain, activeTool, history, onSelect }: Props) {
  const appliedIds = new Set(history.map((h) => h.tool.id))

  // Only show tools that make sense for the currently loaded media.
  // Keeps the palette focused (image work shouldn't show audio/video transforms).
  const visible = domain
    ? transforms.filter(
        (t) =>
          t.domain === domain ||
          // Treat any unexpected domain value as "universal" (future-proof)
          !DOMAIN_ORDER.includes(t.domain as MediaDomain),
      )
    : transforms

  // Group by domain, maintaining declaration order
  const grouped: Array<[string, TransformInfo[]]> = ALL_DOMAINS.reduce<Array<[string, TransformInfo[]]>>(
    (acc, d) => {
      const tools = visible.filter((t) => t.domain === d || (d === 'UNIVERSAL' && !DOMAIN_ORDER.includes(t.domain as MediaDomain)))
      if (tools.length > 0) acc.push([d, tools])
      return acc
    },
    [],
  )

  // Sort: current domain first, then others
  const sorted = domain
    ? [...grouped].sort(([a], [b]) => {
        if (a === domain) return -1
        if (b === domain) return 1
        return 0
      })
    : grouped

  return (
    <div className="flex-1 overflow-y-auto overflow-x-hidden py-1 min-h-0">
      {sorted.map(([groupDomain, tools]) => (
        <div key={groupDomain} className="mb-0.5">
          {/* Group header */}
          <div className="px-3 pt-2.5 pb-1 flex items-center gap-2">
            <span className="text-[8.5px] font-mono uppercase tracking-[0.18em] text-[#2e2e42]">
              {DOMAIN_LABELS[groupDomain] ?? groupDomain}
            </span>
            {domain === groupDomain && (
              <span className="h-px flex-1 bg-[var(--surface-3)]/40" />
            )}
          </div>

          {tools.map((tool) => {
            const isActive = activeTool?.id === tool.id
            const wasApplied = appliedIds.has(tool.id)

            return (
              <motion.button
                key={tool.id}
                onClick={() => onSelect(tool)}
                className={`group w-full flex items-center gap-2 px-3 py-[7px] text-left transition-all duration-100 relative ${
                  isActive
                    ? 'bg-[var(--accent)]/10 text-[#e8e8f0]'
                    : 'text-[#4a4a5e] hover:text-[#8e8ea8] hover:bg-white/[0.02]'
                }`}
                whileTap={{ scale: 0.98 }}
              >
                {/* Active accent bar */}
                {isActive && (
                  <motion.span
                    layoutId="tool-active-bar"
                    className="absolute left-0 top-1 bottom-1 w-[2px] bg-[var(--accent)] rounded-r"
                  />
                )}

                {/* Applied dot / empty */}
                <span
                  className={`w-[5px] h-[5px] rounded-full shrink-0 transition-all ${
                    isActive
                      ? 'bg-[var(--accent)]'
                      : wasApplied
                        ? 'bg-emerald-600/40'
                        : 'bg-[var(--surface-3)]/60 group-hover:bg-[var(--surface-3)]'
                  }`}
                />

                <span className="text-[11px] font-mono flex-1 truncate leading-none">
                  {tool.id}
                </span>

                {wasApplied && !isActive && (
                  <span className="text-[9px] font-mono text-emerald-600/40 shrink-0">✓</span>
                )}
                {isActive && (
                  <span className="text-[9px] font-mono text-[var(--accent)]/50 shrink-0 tabular-nums">
                    ›
                  </span>
                )}
              </motion.button>
            )
          })}
        </div>
      ))}

      {transforms.length === 0 && (
        <div className="flex flex-col items-center justify-center py-16 px-4">
          <p className="text-[9px] font-mono text-[#2a2a38] text-center leading-loose uppercase tracking-widest">
            no tools<br />available
          </p>
        </div>
      )}
    </div>
  )
}
