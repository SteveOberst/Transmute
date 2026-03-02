'use client'

import type { FormatInfo, MediaDomain } from '@/lib/types'
import { motion, AnimatePresence } from 'framer-motion'

/* -- Domain metadata ------------------------------------------------- */
const DOMAINS: MediaDomain[] = ['IMAGE', 'AUDIO', 'VIDEO']

const domainMeta: Record<MediaDomain, {
  label: string
  color: string
  dimColor: string
  borderColor: string
  icon: React.ReactNode
}> = {
  IMAGE: {
    label: 'Image',
    color: '#C084FC',
    dimColor: 'rgba(192, 132, 252, 0.12)',
    borderColor: 'rgba(192, 132, 252, 0.25)',
    icon: (
      <svg width="18" height="18" viewBox="0 0 20 20" fill="none" aria-hidden>
        <rect x="1.5" y="3.5" width="17" height="13" rx="2" stroke="currentColor" strokeWidth="1.5"/>
        <circle cx="7" cy="8" r="1.75" stroke="currentColor" strokeWidth="1.5"/>
        <path d="M1.5 14L6.5 9.5L10 13L13.5 9.5L18.5 15" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" strokeLinecap="round"/>
      </svg>
    ),
  },
  AUDIO: {
    label: 'Audio',
    color: '#34D399',
    dimColor: 'rgba(52, 211, 153, 0.10)',
    borderColor: 'rgba(52, 211, 153, 0.22)',
    icon: (
      <svg width="18" height="18" viewBox="0 0 20 20" fill="none" aria-hidden>
        <rect x="1.5" y="12" width="2.5" height="5" rx="1.25" fill="currentColor"/>
        <rect x="5.25" y="8" width="2.5" height="9" rx="1.25" fill="currentColor"/>
        <rect x="9" y="4" width="2.5" height="13" rx="1.25" fill="currentColor"/>
        <rect x="12.75" y="7" width="2.5" height="10" rx="1.25" fill="currentColor"/>
        <rect x="16.5" y="10" width="2.5" height="7" rx="1.25" fill="currentColor"/>
      </svg>
    ),
  },
  VIDEO: {
    label: 'Video',
    color: '#FB923C',
    dimColor: 'rgba(251, 146, 60, 0.10)',
    borderColor: 'rgba(251, 146, 60, 0.22)',
    icon: (
      <svg width="18" height="18" viewBox="0 0 20 20" fill="none" aria-hidden>
        <rect x="1.5" y="6" width="12" height="9" rx="1.5" stroke="currentColor" strokeWidth="1.5"/>
        <path d="M13.5 9.4L18 7V14L13.5 11.2V9.4Z" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round"/>
        <line x1="1.5" y1="9" x2="13.5" y2="9" stroke="currentColor" strokeWidth="1" opacity="0.35"/>
      </svg>
    ),
  },
}

/* -- Capability pill ------------------------------------------------- */
function CapPill({
  active,
  label,
  activeClass,
}: {
  active: boolean
  label: string
  activeClass: string
}) {
  return (
    <span
      className={`
        inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-mono leading-none
        ${active ? activeClass : 'bg-white/[0.04] text-white/20'}
      `}
    >
      {active ? label : '–'}
    </span>
  )
}

/* -- Single format card --------------------------------------------- */
function FormatCard({ fmt, index }: { fmt: FormatInfo; index: number }) {
  const meta = domainMeta[fmt.domain]
  return (
    <motion.div
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: Math.min(index * 0.02, 0.4), duration: 0.25 }}
      className="group relative flex flex-col rounded-xl overflow-hidden
        border border-[var(--surface-3)] bg-[var(--surface-1)]
        hover:border-white/10 hover:bg-[var(--surface-2)] transition-all duration-200"
    >
      {/* Left accent strip */}
      <div
        className="absolute left-0 top-0 bottom-0 w-[3px] rounded-l-xl"
        style={{ background: meta.color, opacity: 0.6 }}
      />

      <div className="pl-4 pr-3 pt-3 pb-3 flex flex-col gap-2">
        {/* Name row */}
        <div className="flex items-center justify-between gap-2">
          <span className="font-mono text-[13px] font-semibold text-white uppercase tracking-wider leading-none">
            {fmt.name}
          </span>
          {/* Domain icon badge - shown only in the All view */}
          <span className="shrink-0" style={{ color: meta.color, opacity: 0.5 }}>{meta.icon}</span>
        </div>

        {/* Capability tags row */}
        <div className="flex flex-wrap gap-1">
          <CapPill
            active={fmt.canDecode}
            label="↓ decode"
            activeClass="bg-emerald-500/10 text-emerald-400"
          />
          <CapPill
            active={fmt.canEncode}
            label="↑ encode"
            activeClass="bg-sky-500/10 text-sky-400"
          />
          {fmt.hasStructureReader && (
            <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-mono leading-none bg-amber-500/10 text-amber-400">
              ◎ inspect
            </span>
          )}
        </div>

        {/* Provider badge */}
        {fmt.providedBy && (
          <span className="font-mono text-[9px] text-white/20 truncate leading-none">
            {fmt.providedBy}
          </span>
        )}
      </div>
    </motion.div>
  )
}

/* -- Category section header (used in All view) --------------------- */
function CategoryHeader({ domain, count }: { domain: MediaDomain; count: number }) {
  const meta = domainMeta[domain]
  return (
    <div className="flex items-center gap-3 mb-4">
      <div
        className="flex items-center justify-center w-8 h-8 rounded-lg shrink-0"
        style={{ background: meta.dimColor, color: meta.color }}
      >
        {meta.icon}
      </div>
      <span className="font-mono text-xs font-semibold uppercase tracking-[0.12em]" style={{ color: meta.color }}>
        {meta.label}
      </span>
      <span className="font-mono text-[11px] text-white/25 tabular-nums">{count}</span>
      <div className="flex-1 h-px ml-1" style={{ background: meta.borderColor }} />
    </div>
  )
}

/* -- Provider section header ---------------------------------------- */
function ProviderHeader({ provider, count }: { provider: string | null; count: number }) {
  const isBuiltIn = !provider
  return (
    <div className="flex items-center gap-2.5 mb-3">
      <span
        className={`text-[10px] font-mono uppercase tracking-[0.12em] px-2 py-0.5 rounded ${
          isBuiltIn
            ? 'bg-white/5 text-white/35'
            : 'bg-[var(--accent)]/10 text-[var(--accent)]/70'
        }`}
      >
        {isBuiltIn ? 'Built-in' : provider}
      </span>
      <span className="font-mono text-[11px] text-white/20 tabular-nums">{count}</span>
      <div className="flex-1 h-px bg-white/5" />
    </div>
  )
}

/* -- Render formats grouped by provider ----------------------------- */
function FormatsWithProviders({ formats, indexOffset = 0 }: { formats: FormatInfo[]; indexOffset?: number }) {
  const providers = Array.from(new Set(formats.map((f) => f.providedBy ?? null)))
    .sort((a, b) => (a === null ? -1 : b === null ? 1 : a.localeCompare(b)))

  if (providers.length <= 1 && !providers[0]) {
    // All built-in - skip provider header
    return (
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-2.5">
        {formats.map((fmt, i) => <FormatCard key={fmt.name} fmt={fmt} index={indexOffset + i} />)}
      </div>
    )
  }

  let runningIndex = indexOffset
  return (
    <div className="flex flex-col gap-6">
      {providers.map((provider) => {
        const group = formats.filter((f) => (f.providedBy ?? null) === provider)
        const startIdx = runningIndex
        runningIndex += group.length
        return (
          <div key={provider ?? '__builtin'}>
            <ProviderHeader provider={provider} count={group.length} />
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-2.5">
              {group.map((fmt, i) => <FormatCard key={fmt.name} fmt={fmt} index={startIdx + i} />)}
            </div>
          </div>
        )
      })}
    </div>
  )
}

/* -- Main export ---------------------------------------------------- */
interface Props {
  formats: FormatInfo[]
  selectedDomain?: MediaDomain | null
  onDomainChange?: (d: MediaDomain | null) => void
}

export default function FormatGrid({ formats, selectedDomain, onDomainChange }: Props) {
  const all = selectedDomain === null || selectedDomain === undefined

  /* -- Tabs -- */
  return (
    <div>
      {/* Filter tabs */}
      <div className="flex flex-wrap gap-1.5 mb-7">
        {([null, ...DOMAINS] as (MediaDomain | null)[]).map((d) => {
          const active = selectedDomain === d
          const meta = d ? domainMeta[d] : null
          const count = d
            ? formats.filter((f) => f.domain === d).length
            : formats.length
          return (
            <button
              key={d ?? 'all'}
              onClick={() => onDomainChange?.(d)}
              className={`
                flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[11px] font-mono
                uppercase tracking-wider transition-all duration-150 border
                ${active
                  ? d
                    ? 'border-transparent text-[var(--surface-1)] font-semibold'
                    : 'border-[var(--accent)] text-[var(--accent)] bg-[rgba(255,107,53,0.08)]'
                  : 'border-[var(--surface-3)] text-white/35 hover:text-white/60 hover:border-white/15'
                }
              `}
              style={
                active && d && meta
                  ? { background: meta.color, borderColor: 'transparent' }
                  : undefined
              }
            >
              {meta && (
                <span
                  className="size-3.5"
                  style={{ color: active && d ? 'var(--surface-1)' : meta.color }}
                >
                  {meta.icon}
                </span>
              )}
              <span>{d ? meta!.label : 'All'}</span>
              <span
                className={`tabular-nums ${
                  active
                    ? d
                      ? 'opacity-60'
                      : 'text-[var(--accent)]'
                    : 'text-white/20'
                }`}
              >
                {count}
              </span>
            </button>
          )
        })}
      </div>

      {/* Content */}
      <AnimatePresence mode="wait">
        {all ? (
          // Categorised "All" view
          <motion.div
            key="all"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.15 }}
            className="flex flex-col gap-10"
          >
            {DOMAINS.map((domain) => {
              const group = formats.filter((f) => f.domain === domain)
              if (group.length === 0) return null
              return (
                <section key={domain}>
                  <CategoryHeader domain={domain} count={group.length} />
                  <FormatsWithProviders formats={group} />
                </section>
              )
            })}
          </motion.div>
        ) : (
          // Filtered single-domain view
          <motion.div
            key={selectedDomain}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.15 }}
          >
            <div className="">
              <FormatsWithProviders
                formats={formats.filter((f) => f.domain === selectedDomain)}
              />
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {formats.length === 0 && (
        <p className="text-center text-white/20 text-sm py-16 font-mono">
          No formats available
        </p>
      )}
    </div>
  )
}
