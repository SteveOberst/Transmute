'use client'

import { useState } from 'react'
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

      {/* Structure tree */}
      {result.structure && (
        <div className="p-5">
          <div className="flex items-center gap-2 mb-2">
            <span className="text-xs text-[#666680] font-mono uppercase tracking-wider">
              Structure
            </span>
            <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-[var(--surface-2)] text-[#555568] border border-[var(--surface-3)]">
              {result.structure.type}
            </span>
          </div>
          <div className="code-block max-h-96 overflow-y-auto text-xs p-3 rounded-xl bg-[var(--surface-2)] border border-[var(--surface-3)]">
            <JsonTree value={result.structure.value} depth={0} />
          </div>
        </div>
      )}
    </motion.div>
  )
}

/* ── Generic JSON tree renderer ────────────────────────────────────── */

type JsonValue = string | number | boolean | null | JsonValue[] | { [k: string]: JsonValue }

function JsonTree({ value, depth }: { value: unknown; depth: number }) {
  if (value === null || value === undefined) {
    return <span className="text-[#666680]">null</span>
  }
  if (typeof value === 'boolean') {
    return <span className="text-[var(--accent)]">{value ? 'true' : 'false'}</span>
  }
  if (typeof value === 'number') {
    return <span className="text-[#a8d8a8]">{value}</span>
  }
  if (typeof value === 'string') {
    return <span className="text-[#e8c07a]">&quot;{value}&quot;</span>
  }
  if (Array.isArray(value)) {
    return <JsonArray arr={value} depth={depth} />
  }
  if (typeof value === 'object') {
    return <JsonObject obj={value as Record<string, unknown>} depth={depth} />
  }
  return <span className="text-[#888898]">{String(value)}</span>
}

function JsonObject({ obj, depth }: { obj: Record<string, unknown>; depth: number }) {
  const [expanded, setExpanded] = useState(depth < 2)
  const entries = Object.entries(obj)

  if (entries.length === 0) {
    return <span className="text-[#555568]">{'{}'}</span>
  }

  return (
    <span>
      <button
        onClick={() => setExpanded(e => !e)}
        className="text-[#555568] hover:text-[#888898] transition-colors font-mono"
      >
        {expanded ? '▾' : '▸'}
      </button>
      {!expanded && (
        <span className="text-[#555568] ml-1 italic text-[10px]">
          {'{' + entries.length + ' fields}'}
        </span>
      )}
      {expanded && (
        <div className="pl-4 border-l border-[var(--surface-3)]/40 ml-1">
          {entries.map(([k, v]) => (
            <div key={k} className="my-0.5">
              <span className="text-[#888898]">{k}</span>
              <span className="text-[#555568]">: </span>
              <JsonTree value={v} depth={depth + 1} />
            </div>
          ))}
        </div>
      )}
    </span>
  )
}

function JsonArray({ arr, depth }: { arr: unknown[]; depth: number }) {
  const [expanded, setExpanded] = useState(depth < 1 && arr.length <= 8)

  if (arr.length === 0) {
    return <span className="text-[#555568]">[]</span>
  }

  return (
    <span>
      <button
        onClick={() => setExpanded(e => !e)}
        className="text-[#555568] hover:text-[#888898] transition-colors font-mono"
      >
        {expanded ? '▾' : '▸'}
      </button>
      <span className="text-[#555568] ml-1 text-[10px]">[{arr.length}]</span>
      {expanded && (
        <div className="pl-4 border-l border-[var(--surface-3)]/40 ml-1">
          {arr.map((item, i) => (
            <div key={i} className="my-0.5">
              <span className="text-[#555568] text-[10px] mr-1">{i}</span>
              <JsonTree value={item} depth={depth + 1} />
            </div>
          ))}
        </div>
      )}
    </span>
  )
}

/* ── Utility ──────────────────────────────────────────────────────── */

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
}
