'use client'

import { useCallback, useState } from 'react'
import { motion } from 'framer-motion'

interface Props {
  onFile: (file: File) => void
  disabled?: boolean
}

export default function FileDropZone({ onFile, disabled }: Props) {
  const [over, setOver] = useState(false)

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault()
      setOver(false)
      const file = e.dataTransfer.files[0]
      if (file) onFile(file)
    },
    [onFile],
  )

  const handleChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0]
      if (file) onFile(file)
    },
    [onFile],
  )

  return (
    <motion.label
      onDragOver={(e) => {
        e.preventDefault()
        setOver(true)
      }}
      onDragLeave={() => setOver(false)}
      onDrop={handleDrop}
      whileHover={{ scale: 1.005 }}
      whileTap={{ scale: 0.995 }}
      className={`
        relative flex flex-col items-center justify-center gap-4
        w-full min-h-[240px] rounded-2xl cursor-pointer
        border-2 border-dashed transition-all duration-300
        ${
          over
            ? 'border-[var(--accent)] bg-[rgba(255,107,53,0.06)] glow-accent-sm'
            : 'border-[var(--surface-3)] hover:border-[var(--accent)]/50'
        }
        ${disabled ? 'pointer-events-none opacity-40' : ''}
      `}
    >
      {/* Background shimmer */}
      <div className="absolute inset-0 rounded-2xl drop-shimmer opacity-60" />

      {/* Icon */}
      <div className="relative z-10">
        <motion.div
          animate={over ? { y: -6, scale: 1.1 } : { y: 0, scale: 1 }}
          transition={{ type: 'spring', stiffness: 300 }}
        >
          <svg
            width="48"
            height="48"
            viewBox="0 0 24 24"
            fill="none"
            stroke={over ? 'var(--accent)' : '#666680'}
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4" />
            <polyline points="17 8 12 3 7 8" />
            <line x1="12" y1="3" x2="12" y2="15" />
          </svg>
        </motion.div>
      </div>

      <div className="relative z-10 text-center">
        <p className="text-sm text-[#888898]">
          <span className="text-[var(--accent)] font-medium">Choose a file</span>
          {' '}or drag it here
        </p>
        <p className="text-xs text-[#555566] mt-1 font-mono">
          images · audio · video
        </p>
      </div>

      <input
        type="file"
        className="sr-only"
        onChange={handleChange}
        accept="image/*,audio/*,video/*"
        disabled={disabled}
      />
    </motion.label>
  )
}
