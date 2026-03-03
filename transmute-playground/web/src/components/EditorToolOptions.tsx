'use client'

import { motion, AnimatePresence } from 'framer-motion'
import type { TransformInfo, ParameterSchema } from '@/lib/types'
import type { HistoryEntry } from '@/lib/editorTypes'

interface Props {
  activeTool: TransformInfo | null
  params: Record<string, string>
  onChange: (params: Record<string, string>) => void
  onApply: () => void
  onReset: () => void
  history: HistoryEntry[]
  onUndo: () => void
  canUndo: boolean
  processing: boolean
  previewing: boolean
  canApply: boolean
  applyHint?: string
}

function ParamControl({
  schema,
  value,
  onChange,
}: {
  schema: ParameterSchema
  value: string
  onChange: (v: string) => void
}) {
  const { name, type, min, max, enumValues, description } = schema
  const label = name
    .replace(/_/g, ' ')
    .replace(/([A-Z])/g, ' $1')
    .replace(/\b\w/g, (c) => c.toUpperCase())
    .trim()

  return (
    <div className="space-y-1.5">
      <div className="flex items-baseline justify-between gap-2">
        <label className="text-[9.5px] font-mono uppercase tracking-[0.12em] text-[#4a4a5e] shrink-0">
          {label}
        </label>
        {description && (
          <span className="text-[8.5px] font-mono text-[#2e2e40] text-right truncate max-w-[100px]">
            {description}
          </span>
        )}
      </div>

      {type === 'BOOLEAN' ? (
        <button
          role="switch"
          aria-checked={value === 'true'}
          onClick={() => onChange(value === 'true' ? 'false' : 'true')}
          className={`relative w-9 h-5 rounded-full transition-colors duration-200 overflow-hidden ${
            value === 'true' ? 'bg-[var(--accent)]' : 'bg-[var(--surface-3)]'
          }`}
        >
          <span
            className={`absolute top-[3px] left-0 w-[14px] h-[14px] rounded-full bg-white shadow transition-transform duration-200 ${
              value === 'true' ? 'translate-x-[19px]' : 'translate-x-[3px]'
            }`}
          />
        </button>
      ) : type === 'ENUM' ? (
        <select
          value={value}
          onChange={(e) => onChange(e.target.value)}
          className="w-full bg-[var(--surface-2)] border border-[var(--surface-3)] rounded-md px-2.5 py-1.5 font-mono text-[11px] text-[#c8c8d8] outline-none focus:border-[var(--accent)]/40 hover:border-[#333348] transition-colors appearance-none cursor-pointer"
        >
          {enumValues?.map((v) => (
            <option key={v} value={v}>
              {v}
            </option>
          ))}
        </select>
      ) : type === 'INT' || type === 'FLOAT' ? (
        <div className="flex items-center gap-1.5">
          <input
            type="number"
            value={value}
            onChange={(e) => onChange(e.target.value)}
            min={min ?? undefined}
            max={max ?? undefined}
            step={type === 'FLOAT' ? '0.01' : '1'}
            className="flex-1 min-w-0 bg-[var(--surface-2)] border border-[var(--surface-3)] rounded-md px-2.5 py-1.5 font-mono text-[11px] text-[#c8c8d8] outline-none focus:border-[var(--accent)]/40 hover:border-[#333348] transition-colors"
          />
          {(min !== undefined || max !== undefined) && (
            <span className="text-[8.5px] font-mono text-[#2e2e40] shrink-0 tabular-nums">
              {min ?? ''}–{max ?? ''}
            </span>
          )}
        </div>
      ) : (
        <input
          type="text"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          className="w-full bg-[var(--surface-2)] border border-[var(--surface-3)] rounded-md px-2.5 py-1.5 font-mono text-[11px] text-[#c8c8d8] outline-none focus:border-[var(--accent)]/40 hover:border-[#333348] transition-colors"
        />
      )}
    </div>
  )
}

export default function EditorToolOptions({
  activeTool,
  params,
  onChange,
  onApply,
  onReset,
  history,
  onUndo,
  canUndo,
  processing,
  previewing,
  canApply,
  applyHint,
}: Props) {
  const parameters = activeTool?.parameters ?? []

  const handleChange = (name: string, value: string) => {
    onChange({ ...params, [name]: value })
  }

  return (
    <div className="h-full flex flex-col overflow-hidden">
      {/* --- Parameters scroll area --- */}
      <div className="flex-1 min-h-0 overflow-y-auto overflow-x-hidden">
        <AnimatePresence mode="wait">
          {activeTool ? (
            <motion.div
              key={activeTool.id}
              initial={{ opacity: 0, y: 6 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -6 }}
              transition={{ duration: 0.12 }}
              className="p-3 space-y-5"
            >
              {/* Tool name + description */}
              <div className="space-y-1 pb-2 border-b border-[var(--surface-3)]/40">
                <h3 className="font-mono text-[13px] text-[#d8d8e8] leading-none">
                  {activeTool.id}
                </h3>
                {activeTool.description && (
                  <p className="text-[9.5px] font-mono text-[#8e8ea8] opacity-70 leading-relaxed">
                    {activeTool.description}
                  </p>
                )}
              </div>

              {/* Parameters */}
              {parameters.length > 0 ? (
                <div className="space-y-4">
                  {parameters.map((schema) => (
                    <ParamControl
                      key={schema.name}
                      schema={schema}
                      value={params[schema.name] ?? (schema.default ?? '')}
                      onChange={(v) => handleChange(schema.name, v)}
                    />
                  ))}
                </div>
              ) : (
                <p className="text-[9.5px] font-mono text-[#2e2e40] py-1">
                  No configurable parameters.
                </p>
              )}

              {/* Preview indicator */}
              {previewing && (
                <div className="flex items-center gap-1.5">
                  <span className="w-1 h-1 rounded-full bg-[var(--accent)] animate-pulse" />
                  <span className="text-[9px] font-mono text-[#444458] tracking-wide">
                    previewing...
                  </span>
                </div>
              )}
            </motion.div>
          ) : (
            <motion.div
              key="idle"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="flex flex-col items-center justify-center py-12 px-4"
            >
              <p className="text-[9px] font-mono text-[#2a2a38] text-center uppercase tracking-widest leading-loose">
                Select a tool<br />to configure
              </p>
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {/* --- Apply / Reset footer --- */}
      <AnimatePresence>
        {activeTool && (
          <motion.div
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 8 }}
            transition={{ duration: 0.12 }}
            className="shrink-0 px-3 py-3 space-y-1.5 border-t border-[var(--surface-3)]/40"
          >
            {!canApply && !processing && applyHint && (
              <div className="text-[9px] font-mono text-[#8e8ea8] opacity-70 leading-snug">
                Required: <span className="text-[#8e8ea8]">{applyHint}</span>
              </div>
            )}
            <button
              onClick={onApply}
              disabled={processing || !canApply}
              className="w-full flex items-center justify-center gap-2 py-[9px] rounded-lg bg-[var(--accent)] text-white font-mono text-[11px] font-semibold hover:opacity-90 active:scale-[0.98] disabled:opacity-40 disabled:cursor-not-allowed transition-all"
            >
              {processing ? (
                <>
                  <span className="w-3 h-3 border border-white/50 border-t-transparent rounded-full animate-spin" />
                  applying...
                </>
              ) : (
                canApply ? 'Apply' : 'Fill required fields'
              )}
            </button>
            <button
              onClick={onReset}
              disabled={processing}
              className="w-full py-[7px] rounded-lg border border-[var(--surface-3)] text-[#4a4a5e] font-mono text-[11px] hover:text-[#8e8ea8] hover:border-[#333348] transition-colors disabled:opacity-40"
            >
              Reset
            </button>
          </motion.div>
        )}
      </AnimatePresence>

      {/* --- History --- */}
      <div className="shrink-0 border-t border-[var(--surface-3)]/40">
        <div className="flex items-center justify-between px-3 pt-2 pb-1.5">
          <span className="text-[8.5px] font-mono uppercase tracking-[0.18em] text-[#4a4a5e]">
            History
          </span>
          {canUndo && (
            <button
              onClick={onUndo}
              className="text-[9.5px] font-mono text-[#8e8ea8] opacity-70 hover:opacity-100 hover:text-white transition-all"
            >
              ↩ undo
            </button>
          )}
        </div>

        <div className="max-h-[130px] overflow-y-auto pb-2">
          {history.length === 0 ? (
            <p className="text-[9px] font-mono text-[#4a4a5e] px-3 py-1 uppercase tracking-widest">
              empty
            </p>
          ) : (
            [...history].reverse().map((entry, i) => (
              <motion.div
                key={entry.id}
                initial={{ opacity: 0, x: 6 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ duration: 0.1 }}
                className={`flex items-center gap-2 px-3 py-1.5 ${
                  i === 0 ? 'text-[#c8c8d8]' : 'text-[#8e8ea8] opacity-60'
                }`}
              >
                <span
                  className={`text-[9px] shrink-0 ${
                    i === 0 ? 'text-[var(--accent)]/70' : 'text-emerald-600/40'
                  }`}
                >
                  {i === 0 ? '●' : '✓'}
                </span>
                <span className="text-[10px] font-mono truncate">{entry.tool.id}</span>
              </motion.div>
            ))
          )}
        </div>
      </div>
    </div>
  )
}
