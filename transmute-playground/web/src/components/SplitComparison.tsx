'use client'

import { useCallback, useEffect, useRef, useState } from 'react'

interface Props {
  originalSrc: string
  resultSrc: string | null
  processing?: boolean
  type?: 'image' | 'video'
  className?: string
}

/**
 * Squoosh-style draggable split comparison.
 *
 * The result fills the full container; the original is overlaid on the left
 * via `clipPath`. A drag handle lets the user control the split position.
 */
export default function SplitComparison({
  originalSrc,
  resultSrc,
  processing = false,
  type = 'image',
  className = '',
}: Props) {
  const [position, setPosition] = useState(50) // 0-100 %
  const dragging = useRef(false)
  const containerRef = useRef<HTMLDivElement>(null)

  const clamp = (v: number) => Math.max(0, Math.min(100, v))

  const updatePosition = useCallback((clientX: number) => {
    const rect = containerRef.current?.getBoundingClientRect()
    if (!rect) return
    setPosition(clamp(((clientX - rect.left) / rect.width) * 100))
  }, [])

  // Mouse
  const onMouseDown = (e: React.MouseEvent) => {
    dragging.current = true
    updatePosition(e.clientX)
  }

  useEffect(() => {
    const onMove = (e: MouseEvent) => { if (dragging.current) updatePosition(e.clientX) }
    const onUp = () => { dragging.current = false }
    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseup', onUp)
    return () => {
      window.removeEventListener('mousemove', onMove)
      window.removeEventListener('mouseup', onUp)
    }
  }, [updatePosition])

  // Touch
  const onTouchStart = (e: React.TouchEvent) => {
    dragging.current = true
    updatePosition(e.touches[0].clientX)
  }

  useEffect(() => {
    const onMove = (e: TouchEvent) => {
      if (dragging.current) {
        e.preventDefault()
        updatePosition(e.touches[0].clientX)
      }
    }
    const onEnd = () => { dragging.current = false }
    window.addEventListener('touchmove', onMove, { passive: false })
    window.addEventListener('touchend', onEnd)
    return () => {
      window.removeEventListener('touchmove', onMove)
      window.removeEventListener('touchend', onEnd)
    }
  }, [updatePosition])

  const mediaClass = 'w-full h-full object-contain select-none pointer-events-none'

  return (
    <div
      ref={containerRef}
      className={`relative overflow-hidden bg-[var(--surface-1)] rounded-2xl ${className}`}
      style={{ cursor: 'col-resize' }}
      onMouseDown={onMouseDown}
      onTouchStart={onTouchStart}
    >
      {/* -- Result (bottom layer, full width) -------------------------- */}
      <div className="absolute inset-0 flex items-center justify-center">
        {resultSrc && !processing ? (
          type === 'image' ? (
            <img src={resultSrc} alt="Result" className={mediaClass} draggable={false} />
          ) : (
            <video src={resultSrc} className={mediaClass} muted loop autoPlay playsInline />
          )
        ) : (
          <div className="flex flex-col items-center gap-3 text-[#444456]">
            {processing ? (
              <>
                <div className="w-8 h-8 border-2 border-[var(--accent)] border-t-transparent rounded-full animate-spin" />
                <span className="text-xs font-mono">Processing...</span>
              </>
            ) : (
              <span className="text-xs font-mono text-[#333345]">Result will appear here</span>
            )}
          </div>
        )}
      </div>

      {/* Label: Result */}
      {resultSrc && !processing && (
        <div className="absolute bottom-3 right-3 z-10 px-2 py-0.5 rounded text-[10px] font-mono bg-black/60 text-[#aaaacc] pointer-events-none">
          result
        </div>
      )}

      {/* -- Original (top layer, clipped to left side) ------------------ */}
      <div
        className="absolute inset-0 flex items-center justify-center"
        style={{ clipPath: `inset(0 ${100 - position}% 0 0)` }}
      >
        {type === 'image' ? (
          <img src={originalSrc} alt="Original" className={mediaClass} draggable={false} />
        ) : (
          <video src={originalSrc} className={mediaClass} muted loop autoPlay playsInline />
        )}
        {/* Label: Original */}
        <div className="absolute bottom-3 left-3 px-2 py-0.5 rounded text-[10px] font-mono bg-black/60 text-[#aaaacc] pointer-events-none">
          original
        </div>
      </div>

      {/* -- Drag handle ------------------------------------------------ */}
      <div
        className="absolute top-0 bottom-0 z-20 flex items-center justify-center"
        style={{ left: `${position}%`, transform: 'translateX(-50%)' }}
      >
        {/* Line */}
        <div className="absolute top-0 bottom-0 w-0.5 bg-white/70 shadow-[0_0_8px_rgba(255,255,255,0.4)]" />
        {/* Knob */}
        <div className="
          relative z-10 w-8 h-8 rounded-full
          bg-white shadow-lg
          flex items-center justify-center
          pointer-events-none
        ">
          <svg viewBox="0 0 16 16" className="w-4 h-4 text-[#333]" fill="none" stroke="currentColor" strokeWidth={1.5}>
            <path d="M5 8h6M5 8l-2 2M5 8l-2-2M11 8l2 2M11 8l2-2" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </div>
      </div>
    </div>
  )
}
