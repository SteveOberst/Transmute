'use client'

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  useMemo,
  type ReactElement,
  type ReactNode,
} from 'react'
import { AnimatePresence, motion } from 'framer-motion'

/* -- Types -------------------------------------------------------------- */

type ToastKind = 'error' | 'success' | 'info' | 'warning'

interface Toast {
  id: string
  kind: ToastKind
  message: string
  detail?: string
}

interface ToastContextValue {
  toast: (kind: ToastKind, message: string, detail?: string) => void
  error: (message: string, detail?: string) => void
  success: (message: string, detail?: string) => void
  warning: (message: string, detail?: string) => void
  info: (message: string, detail?: string) => void
}

/* -- Context ------------------------------------------------------------- */

const ToastContext = createContext<ToastContextValue>({
  toast: () => {},
  error: () => {},
  success: () => {},
  warning: () => {},
  info: () => {},
})

const DURATION = 4200
let counter = 0

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const timers = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map())

  const dismiss = useCallback((id: string) => {
    timers.current.delete(id)
    setToasts((prev) => prev.filter((t) => t.id !== id))
  }, [])

  const toast = useCallback(
    (kind: ToastKind, message: string, detail?: string) => {
      const id = `toast-${++counter}`
      setToasts((prev) => [...prev.slice(-4), { id, kind, message, detail }])
      const timer = setTimeout(() => dismiss(id), DURATION)
      timers.current.set(id, timer)
    },
    [dismiss],
  )

  useEffect(() => {
    /* cleanup on unmount */
    const t = timers.current
    return () => { t.forEach(clearTimeout) }
  }, [])

  const value = useMemo<ToastContextValue>(() => ({
    toast,
    error: (msg, detail) => toast('error', msg, detail),
    success: (msg, detail) => toast('success', msg, detail),
    warning: (msg, detail) => toast('warning', msg, detail),
    info: (msg, detail) => toast('info', msg, detail),
  }), [toast])

  return (
    <ToastContext.Provider value={value}>
      {children}
      <ToastContainer toasts={toasts} onDismiss={dismiss} />
    </ToastContext.Provider>
  )
}

export function useToast(): ToastContextValue {
  return useContext(ToastContext)
}

/* -- Icons --------------------------------------------------------------- */

const icons: Record<ToastKind, ReactElement> = {
  error: (
    <svg viewBox="0 0 16 16" className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="1.5">
      <circle cx="8" cy="8" r="6.5" />
      <path d="M8 5v3.5M8 11h.01" strokeLinecap="round" />
    </svg>
  ),
  success: (
    <svg viewBox="0 0 16 16" className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="1.5">
      <circle cx="8" cy="8" r="6.5" />
      <path d="M5 8l2.5 2.5L11 5.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  ),
  warning: (
    <svg viewBox="0 0 16 16" className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="1.5">
      <path d="M8 2L14.5 13.5H1.5L8 2z" strokeLinejoin="round" />
      <path d="M8 6.5V9.5M8 11.5h.01" strokeLinecap="round" />
    </svg>
  ),
  info: (
    <svg viewBox="0 0 16 16" className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="1.5">
      <circle cx="8" cy="8" r="6.5" />
      <path d="M8 7.5V11M8 5h.01" strokeLinecap="round" />
    </svg>
  ),
}

const palette: Record<ToastKind, { bg: string; border: string; icon: string; bar: string }> = {
  error:   { bg: 'var(--toast-error-bg)',   border: 'var(--toast-error-border)',   icon: '#f87171', bar: '#f87171' },
  success: { bg: 'var(--toast-success-bg)', border: 'var(--toast-success-border)', icon: '#34d399', bar: '#34d399' },
  warning: { bg: 'var(--toast-warn-bg)',    border: 'var(--toast-warn-border)',    icon: '#fbbf24', bar: '#fbbf24' },
  info:    { bg: 'var(--toast-info-bg)',    border: 'var(--toast-info-border)',    icon: '#60a5fa', bar: '#60a5fa' },
}

/* -- Container ------------------------------------------------------------ */

function ToastContainer({
  toasts,
  onDismiss,
}: {
  toasts: Toast[]
  onDismiss: (id: string) => void
}) {
  return (
    <div
      aria-live="assertive"
      className="fixed bottom-5 right-5 z-[9999] flex flex-col gap-2 items-end pointer-events-none"
    >
      <AnimatePresence initial={false}>
        {toasts.map((t) => (
          <ToastItem key={t.id} toast={t} onDismiss={onDismiss} />
        ))}
      </AnimatePresence>
    </div>
  )
}

/* -- Single Toast --------------------------------------------------------- */

function ToastItem({ toast: t, onDismiss }: { toast: Toast; onDismiss: (id: string) => void }) {
  const p = palette[t.kind]

  return (
    <motion.div
      layout
      initial={{ opacity: 0, x: 32, scale: 0.92 }}
      animate={{ opacity: 1, x: 0, scale: 1 }}
      exit={{ opacity: 0, x: 32, scale: 0.88, transition: { duration: 0.2 } }}
      transition={{ type: 'spring', stiffness: 420, damping: 36 }}
      className="pointer-events-auto relative overflow-hidden rounded-xl max-w-sm w-full"
      style={{
        background: p.bg,
        border: `1px solid ${p.border}`,
        backdropFilter: 'blur(12px)',
        boxShadow: '0 8px 32px rgba(0,0,0,0.18)',
      }}
    >
      {/* Progress bar */}
      <motion.div
        className="absolute bottom-0 left-0 h-[2px] origin-left"
        style={{ background: p.bar }}
        initial={{ scaleX: 1 }}
        animate={{ scaleX: 0 }}
        transition={{ duration: DURATION / 1000, ease: 'linear' }}
      />

      <div className="flex items-start gap-3 px-4 py-3">
        {/* Icon */}
        <span className="shrink-0 mt-0.5" style={{ color: p.icon }}>
          {icons[t.kind]}
        </span>

        {/* Text */}
        <div className="flex-1 min-w-0">
          <p className="text-[13px] font-medium leading-snug text-[var(--toast-text)] truncate">
            {t.message}
          </p>
          {t.detail && (
            <p className="text-[11px] mt-0.5 text-[var(--toast-subtext)] line-clamp-2">
              {t.detail}
            </p>
          )}
        </div>

        {/* Dismiss */}
        <button
          onClick={() => onDismiss(t.id)}
          className="shrink-0 text-[var(--toast-subtext)] hover:text-[var(--toast-text)] transition-colors duration-150 mt-0.5"
          aria-label="Dismiss notification"
        >
          <svg viewBox="0 0 12 12" className="w-3 h-3" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
            <path d="M2 2l8 8M10 2l-8 8" />
          </svg>
        </button>
      </div>
    </motion.div>
  )
}
