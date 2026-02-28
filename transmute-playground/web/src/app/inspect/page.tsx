'use client'

import { useState, useCallback, useEffect, useRef } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import Navbar from '@/components/Navbar'
import FileDropZone from '@/components/FileDropZone'
import MediaPreview from '@/components/MediaPreview'
import InspectPanel from '@/components/InspectPanel'
import { uploadFile, inspectFile } from '@/lib/api'
import { formatBytes } from '@/lib/utils'
import { useToast } from '@/components/Toast'
import type { FileHandle, InspectResult, MediaDomain } from '@/lib/types'

export default function InspectPage() {
  const [file, setFile] = useState<File | null>(null)
  const [localUrl, setLocalUrl] = useState<string | null>(null)
  const [handle, setHandle] = useState<FileHandle | null>(null)
  const [result, setResult] = useState<InspectResult | null>(null)
  const [loading, setLoading] = useState(false)
  const toast = useToast()

  const localUrlRef = useRef<string | null>(null)
  useEffect(() => () => {
    if (localUrlRef.current) URL.revokeObjectURL(localUrlRef.current)
  }, [])

  const handleFile = useCallback(async (dropped: File) => {
    setResult(null)

    if (localUrlRef.current) URL.revokeObjectURL(localUrlRef.current)
    const blobUrl = URL.createObjectURL(dropped)
    localUrlRef.current = blobUrl
    setLocalUrl(blobUrl)
    setFile(dropped)

    setLoading(true)
    try {
      const h = await uploadFile(dropped)
      setHandle(h)
      const insp = await inspectFile(h.handle)
      setResult(insp)
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : 'Inspect failed')
    } finally {
      setLoading(false)
    }
  }, [])

  const reset = useCallback(() => {
    setFile(null)
    setHandle(null)
    setResult(null)
    if (localUrlRef.current) {
      URL.revokeObjectURL(localUrlRef.current)
      localUrlRef.current = null
    }
    setLocalUrl(null)
  }, [])

  const domain: MediaDomain = result?.domain ?? 'IMAGE'

  return (
    <div className="h-dvh flex flex-col overflow-hidden">
      <Navbar />

      <div className="flex flex-col flex-1 overflow-hidden">
        <AnimatePresence mode="wait">
          {!localUrl ? (
            /* ── Empty state ──────────────────────────────────────── */
            <motion.div
              key="empty"
              initial={{ opacity: 0, scale: 0.97 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.97 }}
              className="flex-1 flex flex-col items-center justify-center px-6 pb-16"
            >
              <h1 className="font-display text-4xl sm:text-5xl text-white tracking-tight mb-3">
                Inspect
              </h1>
              <p className="text-[#555568] text-sm mb-10 max-w-sm text-center">
                Upload any media file to explore its structure, format metadata, and properties.
              </p>
              <div className="w-full max-w-lg">
                <FileDropZone onFile={handleFile} disabled={false} />
              </div>
            </motion.div>
          ) : (
            /* ── Inspect view ─────────────────────────────────────── */
            <motion.div
              key="inspect"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="flex-1 flex flex-col overflow-hidden"
            >
              {/* Header */}
              <div className="flex items-center gap-3 px-4 pt-3 pb-2 shrink-0 border-b border-[var(--surface-3)]/40">
                <div className="
                  flex items-center gap-2 px-3 py-1 rounded-full
                  bg-[var(--surface-2)] border border-[var(--surface-3)]
                  text-xs font-mono text-[#888898] max-w-xs truncate
                ">
                  <span className="w-1.5 h-1.5 rounded-full bg-[var(--accent)] shrink-0" />
                  <span className="truncate">{file?.name}</span>
                  {result && (
                    <span className="text-[var(--accent)] uppercase ml-1 shrink-0">
                      {result.format}
                    </span>
                  )}
                  {loading && (
                    <span className="text-[#555568] ml-1 shrink-0 animate-pulse">inspecting…</span>
                  )}
                </div>

                {result && (
                  <div className="flex items-center gap-3 text-xs font-mono text-[#555568]">
                    <span className="capitalize">{result.domain.toLowerCase()}</span>
                    <span>·</span>
                    <span>{formatBytes(result.fileSize)}</span>
                  </div>
                )}

                <button
                  onClick={reset}
                  className="ml-auto text-[#444456] hover:text-[#888898] text-xs font-mono transition-colors"
                >
                  ✕ clear
                </button>
              </div>

              {/* Two-pane body */}
              <div className="flex-1 flex overflow-hidden min-h-0">
                {/* Left: media preview */}
                <div className="w-2/5 flex-shrink-0 flex items-center justify-center p-4 border-r border-[var(--surface-3)]/40 bg-[var(--surface-1)]/30">
                  <MediaPreview
                    src={localUrl}
                    domain={domain}
                    className="max-h-full max-w-full rounded-xl"
                  />
                </div>

                {/* Right: inspect panel */}
                <div className="flex-1 overflow-y-auto p-4">
                  {loading && !result && (
                    <div className="flex items-center gap-3 text-[#444456] mt-8 ml-4">
                      <div className="w-5 h-5 border-2 border-[var(--accent)] border-t-transparent rounded-full animate-spin" />
                      <span className="text-xs font-mono">Analyzing file structure…</span>
                    </div>
                  )}
                  {result && <InspectPanel result={result} />}
                </div>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  )
}

