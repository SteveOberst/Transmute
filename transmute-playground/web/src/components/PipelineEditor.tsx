'use client'

import type { TransformInfo, TransformStep, MediaDomain } from '@/lib/types'
import { motion, AnimatePresence, Reorder } from 'framer-motion'
import { useState, useCallback, useRef, useEffect } from 'react'

interface Props {
  transforms: TransformInfo[]
  domain: MediaDomain
  pipeline: TransformStep[]
  onChange: (pipeline: TransformStep[]) => void
}

export default function PipelineEditor({ transforms, domain, pipeline, onChange }: Props) {
  const available = transforms.filter((t) => t.domain === domain)
  const [search, setSearch] = useState('')
  const [showPicker, setShowPicker] = useState(false)
  const pickerRef = useRef<HTMLDivElement>(null)

  const filtered = available.filter(
    (t) =>
      t.id.toLowerCase().includes(search.toLowerCase()) ||
      t.description.toLowerCase().includes(search.toLowerCase()),
  )

  // Close picker when clicking outside
  useEffect(() => {
    if (!showPicker) return
    const handler = (e: MouseEvent) => {
      if (pickerRef.current && !pickerRef.current.contains(e.target as Node)) {
        setShowPicker(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [showPicker])

  const addStep = useCallback(
    (transformId: string) => {
      const tf = available.find((t) => t.id === transformId)
      if (!tf) return
      const step: TransformStep = { transformId, parameters: {} }
      if (tf.parameters) {
        const defaults: Record<string, string> = {}
        for (const p of tf.parameters) {
          if (p.default) defaults[p.name] = p.default
        }
        step.parameters = defaults
      }
      onChange([...pipeline, step])
      setSearch('')
      setShowPicker(false)
    },
    [available, pipeline, onChange],
  )

  const removeStep = useCallback(
    (idx: number) => onChange(pipeline.filter((_, i) => i !== idx)),
    [pipeline, onChange],
  )

  const updateParam = useCallback(
    (idx: number, paramName: string, value: string) => {
      const next = [...pipeline]
      next[idx] = { ...next[idx], parameters: { ...next[idx].parameters, [paramName]: value } }
      onChange(next)
    },
    [pipeline, onChange],
  )

  return (
    <div className="space-y-3">
      {/* Transform picker */}
      <div className="relative" ref={pickerRef}>
        <button
          onClick={() => setShowPicker((v) => !v)}
          className="
            w-full flex items-center justify-between px-3 py-2 rounded-lg
            bg-[var(--surface-2)] border border-[var(--surface-3)]
            hover:border-white/15 transition-colors text-left
          "
        >
          <span className="font-mono text-xs text-white/40">＋ add transform...</span>
          <span className="font-mono text-[10px] text-white/20 uppercase tracking-widest">
            {available.length} available
          </span>
        </button>

        <AnimatePresence>
          {showPicker && (
            <motion.div
              key="picker"
              initial={{ opacity: 0, y: -4, scale: 0.98 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: -4, scale: 0.98 }}
              transition={{ duration: 0.1 }}
              className="
                absolute top-full left-0 right-0 mt-1 z-50
                bg-[var(--surface-1)] border border-[var(--surface-3)]
                rounded-xl shadow-2xl shadow-black/60 overflow-hidden
              "
            >
              <div className="px-3 py-2 border-b border-[var(--surface-3)]/60">
                <input
                  autoFocus
                  type="text"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder="Search transforms..."
                  className="
                    w-full bg-transparent font-mono text-xs text-white/80
                    placeholder:text-white/20 outline-none
                  "
                />
              </div>
              <div className="max-h-48 overflow-y-auto">
                {filtered.length === 0 ? (
                  <div className="px-3 py-4 font-mono text-xs text-white/20 text-center">
                    No transforms match
                  </div>
                ) : (
                  filtered.map((t) => (
                    <button
                      key={t.id}
                      onClick={() => addStep(t.id)}
                      className="
                        w-full flex items-baseline gap-3 px-3 py-2.5
                        hover:bg-[var(--surface-2)] transition-colors text-left
                        border-b border-[var(--surface-3)]/30 last:border-0
                      "
                    >
                      <span className="font-mono text-xs text-white shrink-0">{t.id}</span>
                      <span className="font-mono text-[11px] text-white/30 truncate">{t.description}</span>
                    </button>
                  ))
                )}
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {/* Steps */}
      <AnimatePresence mode="popLayout">
        {pipeline.length === 0 ? (
          <motion.div
            key="empty"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="
              text-center py-5 border border-dashed border-[var(--surface-3)]/70
              rounded-xl text-white/20 text-xs font-mono
            "
          >
            pipeline empty — add a transform above
          </motion.div>
        ) : (
          <Reorder.Group
            key="steps"
            axis="y"
            values={pipeline}
            onReorder={onChange}
            className="space-y-1.5"
          >
            {pipeline.map((step, idx) => {
              const tf = available.find((t) => t.id === step.transformId)
              const hasParams = tf?.parameters && tf.parameters.length > 0
              return (
                <Reorder.Item
                  key={`${step.transformId}__${idx}`}
                  value={step}
                  initial={{ opacity: 0, x: -16 }}
                  animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0, height: 0 }}
                  className="
                    rounded-xl bg-[var(--surface-2)] border border-[var(--surface-3)]/80
                    hover:border-white/10 overflow-hidden cursor-grab active:cursor-grabbing
                  "
                >
                  {/* Header row */}
                  <div className="flex items-center gap-2.5 px-3 py-2.5">
                    <span className="
                      shrink-0 w-5 h-5 rounded-md flex items-center justify-center
                      bg-[var(--accent)]/12 text-[var(--accent)] text-[10px] font-mono font-bold
                    ">
                      {idx + 1}
                    </span>
                    <span className="font-mono text-xs text-white/80 font-medium">{step.transformId}</span>
                    {tf && <span className="text-[11px] text-white/25 truncate flex-1">{tf.description}</span>}
                    <button
                      onClick={() => removeStep(idx)}
                      className="
                        ml-auto shrink-0 w-5 h-5 flex items-center justify-center rounded
                        text-white/20 hover:text-red-400 hover:bg-red-500/10 transition-colors
                        font-mono text-xs
                      "
                    >
                      ✕
                    </button>
                  </div>

                  {/* Inline parameters */}
                  {hasParams && (
                    <div className="px-3 pb-3 grid grid-cols-2 gap-2">
                      {tf!.parameters!.map((p) => {
                        const val = step.parameters?.[p.name] ?? p.default ?? ''
                        return (
                          <div key={p.name} className="flex flex-col gap-0.5">
                            <label className="font-mono text-[10px] text-white/30 uppercase tracking-wider">
                              {p.name}
                              {p.required && <span className="text-[var(--accent)] ml-0.5">*</span>}
                            </label>
                            {p.type === 'ENUM' && p.enumValues ? (
                              <select
                                value={val}
                                onChange={(e) => updateParam(idx, p.name, e.target.value)}
                                className="
                                  bg-[var(--surface-1)] border border-[var(--surface-3)]
                                  rounded-md px-2 py-1 font-mono text-xs text-white/80 outline-none
                                  focus:border-[var(--accent)]/50 transition-colors
                                "
                              >
                                {p.enumValues.map((v) => (
                                  <option key={v} value={v}>{v}</option>
                                ))}
                              </select>
                            ) : p.type === 'BOOLEAN' ? (
                              <div className="flex items-center gap-2 h-7">
                                <button
                                  onClick={() =>
                                    updateParam(idx, p.name, val === 'true' ? 'false' : 'true')
                                  }
                                  className={`relative w-8 h-[18px] rounded-full transition-colors ${
                                    val === 'true' ? 'bg-[var(--accent)]' : 'bg-[var(--surface-3)]'
                                  }`}
                                >
                                  <span
                                    className={`absolute top-[2px] w-[14px] h-[14px] rounded-full bg-white transition-transform ${
                                      val === 'true' ? 'translate-x-[18px]' : 'translate-x-[2px]'
                                    }`}
                                  />
                                </button>
                                <span className="font-mono text-[11px] text-white/40">{val === 'true' ? 'on' : 'off'}</span>
                              </div>
                            ) : (
                              <input
                                type={p.type === 'INT' || p.type === 'FLOAT' ? 'number' : 'text'}
                                value={val}
                                placeholder={p.default ?? p.description}
                                onChange={(e) => updateParam(idx, p.name, e.target.value)}
                                className="
                                  bg-[var(--surface-1)] border border-[var(--surface-3)]
                                  rounded-md px-2 py-1 font-mono text-xs text-white/80 outline-none
                                  placeholder:text-white/15
                                  focus:border-[var(--accent)]/50 transition-colors
                                "
                              />
                            )}
                          </div>
                        )
                      })}
                    </div>
                  )}
                </Reorder.Item>
              )
            })}
          </Reorder.Group>
        )}
      </AnimatePresence>
    </div>
  )
}
