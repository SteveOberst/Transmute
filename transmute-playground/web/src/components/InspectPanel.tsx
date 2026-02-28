'use client'

import type { InspectResult } from '@/lib/types'
import { motion } from 'framer-motion'

interface Props {
  result: InspectResult
}

export default function InspectPanel({ result }: Props) {
  const domainClass =
    result.domain === 'AUDIO' ? 'chip-audio'
    : result.domain === 'VIDEO' ? 'chip-video'
    : 'chip-image'

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      className="
        rounded-2xl border border-[var(--surface-3)]
        bg-[var(--surface-1)] overflow-hidden
      "
    >
      {/* Header bar */}
      <div className="
        flex items-center justify-between px-5 py-3
        bg-[var(--surface-2)] border-b border-[var(--surface-3)]
      ">
        <div className="flex items-center gap-3">
          <span className={`text-[10px] font-mono px-2 py-0.5 rounded ${domainClass}`}>
            {result.domain}
          </span>
          <span className="font-mono text-white text-sm font-medium uppercase">
            {result.format}
          </span>
        </div>
        <span className="font-mono text-xs text-[#666680]">
          {formatBytes(result.fileSize)}
        </span>
      </div>

      {/* Properties table */}
      <div className="p-5">
        <table className="w-full text-sm">
          <tbody>
            {Object.entries(result.properties).map(([key, value], i) => (
              <motion.tr
                key={key}
                initial={{ opacity: 0, x: -8 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: i * 0.04 }}
                className="border-b border-[var(--surface-3)]/50 last:border-0"
              >
                <td className="py-2 pr-4 font-mono text-xs text-[#888898] whitespace-nowrap">
                  {key}
                </td>
                <td className="py-2 font-mono text-xs text-white">
                  {value}
                </td>
              </motion.tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Structure tree (if available) */}
      {result.structure && (
        <div className="px-5 pb-5">
          <div className="text-xs text-[#666680] font-mono mb-2 uppercase tracking-wider">
            Structure
          </div>
          <div className="code-block max-h-60 overflow-y-auto text-xs">
            <StructureTree node={result.structure} depth={0} />
          </div>
        </div>
      )}
    </motion.div>
  )
}

/* ── Structure tree renderer ──────────────────────────────────────── */

function StructureTree({ node, depth }: { node: InspectResult['structure']; depth: number }) {
  if (!node) return null
  const indent = '  '.repeat(depth)
  return (
    <>
      <div>
        <span className="text-[var(--accent)]">{indent}{node.name}</span>
        <span className="text-[#555566]"> ({node.type})</span>
        {node.size > 0 && (
          <span className="text-[#444455]"> [{formatBytes(node.size)}]</span>
        )}
      </div>
      {Object.entries(node.properties).map(([k, v]) => (
        <div key={k} className="text-[#666680]">
          {indent}  {k}: <span className="text-[#999]">{v}</span>
        </div>
      ))}
      {node.children?.map((child, i) => (
        <StructureTree key={i} node={child} depth={depth + 1} />
      ))}
    </>
  )
}

/* ── Utility ──────────────────────────────────────────────────────── */

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
}
