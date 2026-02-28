'use client'

import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import Navbar from '@/components/Navbar'
import FormatGrid from '@/components/FormatGrid'
import { fetchFormats } from '@/lib/api'
import type { FormatInfo, MediaDomain } from '@/lib/types'

export default function FormatsPage() {
  const [formats, setFormats] = useState<FormatInfo[]>([])
  const [domain, setDomain] = useState<MediaDomain | null>(null)
  const [loaded, setLoaded] = useState(false)

  useEffect(() => {
    fetchFormats()
      .then((data) => {
        setFormats(data)
        setLoaded(true)
      })
      .catch(() => setLoaded(true))
  }, [])

  const counts = {
    total: formats.length,
    image: formats.filter((f) => f.domain === 'IMAGE').length,
    audio: formats.filter((f) => f.domain === 'AUDIO').length,
    video: formats.filter((f) => f.domain === 'VIDEO').length,
  }

  return (
    <div className="min-h-dvh">
      <Navbar />

      <main className="max-w-6xl mx-auto px-6 py-10">
        {/* Header */}
        <div className="mb-10">
          <motion.h1
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            className="font-display text-4xl sm:text-5xl text-white tracking-tight"
          >
            Formats
          </motion.h1>
          <motion.p
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.05 }}
            className="text-[#666680] text-sm mt-2 max-w-lg"
          >
            Media formats supported by the current Transmute instance.
          </motion.p>

          {/* Stats bar */}
          {loaded && (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.1 }}
              className="flex gap-6 mt-6"
            >
              {Object.entries(counts).map(([key, val]) => (
                <div key={key} className="flex items-baseline gap-1.5">
                  <span className="font-mono text-xl text-white font-bold">{val}</span>
                  <span className="font-mono text-xs text-[#555566] uppercase">{key}</span>
                </div>
              ))}
            </motion.div>
          )}
        </div>

        {/* Grid */}
        {loaded ? (
          <FormatGrid
            formats={formats}
            selectedDomain={domain}
            onDomainChange={setDomain}
          />
        ) : (
          <div className="flex justify-center py-20">
            <div className="w-6 h-6 border-2 border-[var(--accent)] border-t-transparent rounded-full animate-spin" />
          </div>
        )}
      </main>
    </div>
  )
}
