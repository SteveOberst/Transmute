'use client'

import { useState, useCallback, useEffect, useRef } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import Navbar from '@/components/Navbar'
import FileDropZone from '@/components/FileDropZone'
import PipelineEditor from '@/components/PipelineEditor'
import SplitComparison from '@/components/SplitComparison'
import {
  uploadFile,
  inspectFile,
  fetchFormats,
  fetchTransforms,
  executeTransform,
  fileUrl,
} from '@/lib/api'
import { formatBytes } from '@/lib/utils'
import { useToast } from '@/components/Toast'
import type {
  FileHandle,
  InspectResult,
  FormatInfo,
  TransformInfo,
  TransformStep,
  TransformResult,
  MediaDomain,
} from '@/lib/types'

export default function TransformPage() {
  const [file, setFile] = useState<File | null>(null)
  const [localOriginalUrl, setLocalOriginalUrl] = useState<string | null>(null)
  const [handle, setHandle] = useState<FileHandle | null>(null)
  const [inspection, setInspection] = useState<InspectResult | null>(null)

  const [formats, setFormats] = useState<FormatInfo[]>([])
  const [transforms, setTransforms] = useState<TransformInfo[]>([])

  const [pipeline, setPipeline] = useState<TransformStep[]>([])
  const [outputFormat, setOutputFormat] = useState<string>('')

  const [result, setResult] = useState<TransformResult | null>(null)
  const [resultUrl, setResultUrl] = useState<string | null>(null)

  const [processing, setProcessing] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [pipelineOpen, setPipelineOpen] = useState(false)
  const toast = useToast()

  const localUrlRef = useRef<string | null>(null)

  useEffect(() => {
    return () => { if (localUrlRef.current) URL.revokeObjectURL(localUrlRef.current) }
  }, [])

  useEffect(() => {
    fetchFormats().then(setFormats).catch(() => {})
    fetchTransforms().then(setTransforms).catch(() => {})
  }, [])

  const handleFile = useCallback(async (dropped: File) => {
    setResult(null); setResultUrl(null); setPipeline([])
    if (localUrlRef.current) URL.revokeObjectURL(localUrlRef.current)
    const blobUrl = URL.createObjectURL(dropped)
    localUrlRef.current = blobUrl
    setLocalOriginalUrl(blobUrl)
    setFile(dropped)
    setUploading(true)
    try {
      const h = await uploadFile(dropped)
      setHandle(h)
      const insp = await inspectFile(h.handle)
      setInspection(insp)
      setOutputFormat(insp.format.toLowerCase())
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : 'Upload failed')
    } finally { setUploading(false) }
  }, [toast])

  const handleTransform = useCallback(async (
    currentHandle: FileHandle,
    currentFormat: string,
    currentPipeline: TransformStep[],
  ) => {
    setProcessing(true)
    try {
      const res = await executeTransform({
        fileHandle: currentHandle.handle,
        outputFormat: currentFormat,
        pipeline: currentPipeline,
      })
      setResult(res)
      setResultUrl(fileUrl(res.resultHandle))
      toast.success('Transform complete', `${formatBytes(res.fileSize)} · ${res.durationMs}ms`)
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : 'Transform failed')
    } finally { setProcessing(false) }
  }, [toast])

  const autoRunTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  useEffect(() => {
    if (!handle || !outputFormat || uploading) return
    if (autoRunTimer.current) clearTimeout(autoRunTimer.current)
    autoRunTimer.current = setTimeout(() => {
      handleTransform(handle, outputFormat, pipeline)
    }, 700)
    return () => { if (autoRunTimer.current) clearTimeout(autoRunTimer.current) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [handle, outputFormat, pipeline, uploading])

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
        e.preventDefault()
        if (handle && !uploading && !processing) handleTransform(handle, outputFormat, pipeline)
      }
      if ((e.ctrlKey || e.metaKey) && e.key === 's') {
        e.preventDefault()
        if (result && resultUrl) {
          const a = document.createElement('a')
          a.href = resultUrl; a.download = ''; a.click()
        }
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [handle, uploading, processing, handleTransform, outputFormat, pipeline, result, resultUrl])

  const reset = useCallback(() => {
    setFile(null); setHandle(null); setInspection(null); setPipeline([])
    setOutputFormat(''); setResult(null); setResultUrl(null)
    setPipelineOpen(false)
    if (localUrlRef.current) { URL.revokeObjectURL(localUrlRef.current); localUrlRef.current = null }
    setLocalOriginalUrl(null)
  }, [])

  const domain: MediaDomain = inspection?.domain ?? 'IMAGE'
  const isAudio = domain === 'AUDIO'
  const encodableFormats = formats.filter((f) => f.domain === domain && f.canEncode)

  return (
    <div className="h-dvh flex flex-col overflow-hidden">
      <Navbar />
      <div className="flex flex-col flex-1 overflow-hidden">
        <AnimatePresence mode="wait">
          {!localOriginalUrl ? (
            <motion.div key="empty"
              initial={{ opacity: 0, scale: 0.97 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.97 }}
              className="flex-1 flex flex-col items-center justify-center px-6 pb-16"
            >
              <h1 className="font-display text-4xl sm:text-5xl text-white tracking-tight mb-3">Transform</h1>
              <p className="text-[#555568] text-sm mb-10 max-w-sm text-center">
                Drop a media file to convert format, apply transforms, and preview results live.
              </p>
              <div className="w-full max-w-lg"><FileDropZone onFile={handleFile} disabled={false} /></div>
            </motion.div>
          ) : (
            <motion.div key="comparison"
              initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
              className="flex-1 flex flex-col overflow-hidden"
            >
              {/* File info bar */}
              <div className="flex items-center gap-3 px-4 pt-2.5 pb-2 shrink-0">
                <div className="flex items-center gap-2 px-2.5 py-1 rounded-full bg-[var(--surface-2)] border border-[var(--surface-3)] text-xs font-mono text-[#888898] max-w-xs truncate">
                  <span className="w-1.5 h-1.5 rounded-full bg-[var(--accent)] shrink-0" />
                  <span className="truncate">{file?.name}</span>
                  {inspection && <span className="text-[var(--accent)] uppercase ml-1 shrink-0">{inspection.format}</span>}
                  {uploading && <span className="text-[#555568] ml-1 shrink-0 animate-pulse">uploading...</span>}
                </div>
                {result && (
                  <div className="flex items-center gap-1.5 text-xs font-mono text-[#555568]">
                    <span className="text-emerald-400">v</span>
                    <span>{formatBytes(result.fileSize)}</span>
                    <span className="text-[#333345]">.</span>
                    <span>{result.durationMs}ms</span>
                  </div>
                )}
                <button onClick={reset} className="ml-auto text-[#444456] hover:text-[#888898] text-xs font-mono transition-colors">x clear</button>
              </div>

              {/* Media area - relative container for overlay */}
              <div className="flex-1 relative min-h-0">
                {isAudio ? (
                  <div className="absolute inset-0 flex flex-col items-center justify-center gap-8 px-8 py-4 overflow-auto">
                    <AudioPlayer label="original" src={localOriginalUrl} />
                    <div className="w-full max-w-xl border-t border-[var(--surface-3)]" />
                    {resultUrl
                      ? <AudioPlayer label="result" src={resultUrl} accent />
                      : <div className="w-full max-w-xl h-16 rounded-xl border border-dashed border-[var(--surface-3)] flex items-center justify-center">
                          <span className="text-xs font-mono text-[#333345]">{processing ? 'Processing...' : 'Result will appear here'}</span>
                        </div>
                    }
                  </div>
                ) : (
                  <div className="absolute inset-0 px-4 pb-1">
                    <SplitComparison
                      originalSrc={localOriginalUrl} resultSrc={resultUrl}
                      processing={processing} type={domain === 'VIDEO' ? 'video' : 'image'} className="h-full"
                    />
                  </div>
                )}

                {/* Pipeline overlay panel */}
                <AnimatePresence>
                  {pipelineOpen && (
                    <motion.div key="pipeline-panel"
                      initial={{ y: '100%' }} animate={{ y: 0 }} exit={{ y: '100%' }}
                      transition={{ type: 'spring', stiffness: 400, damping: 40 }}
                      className="absolute inset-x-0 bottom-0 z-20 bg-[var(--surface-0)]/97 backdrop-blur-xl border-t border-[var(--surface-3)]/60 max-h-[65%] flex flex-col overflow-hidden"
                    >
                      <div className="flex items-center justify-between px-4 py-2.5 border-b border-[var(--surface-3)]/50 shrink-0">
                        <span className="text-[11px] font-mono text-white/40 uppercase tracking-widest">
                          Pipeline
                          {pipeline.length > 0 && (
                            <span className="ml-2 px-1.5 py-0.5 rounded bg-[var(--accent)]/15 text-[var(--accent)]">{pipeline.length}</span>
                          )}
                        </span>
                        <button onClick={() => setPipelineOpen(false)} className="text-white/25 hover:text-white/70 font-mono text-xs transition-colors">x close</button>
                      </div>
                      <div className="flex-1 overflow-y-auto px-4 py-3">
                        {transforms.length > 0
                          ? <PipelineEditor transforms={transforms} domain={domain} pipeline={pipeline} onChange={setPipeline} />
                          : <p className="text-xs font-mono text-white/20 text-center py-8">No transforms available for {domain.toLowerCase()}</p>
                        }
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>

              {/* Bottom bar */}
              <div className="shrink-0 flex items-center gap-2 px-4 py-2.5 border-t border-[var(--surface-3)]/60 bg-[var(--surface-0)]/90 backdrop-blur-xl">
                <div className="flex items-center gap-1.5">
                  <span className="font-mono text-[10px] text-white/30 uppercase tracking-widest shrink-0">to</span>
                  <select
                    value={outputFormat} onChange={(e) => setOutputFormat(e.target.value)}
                    disabled={encodableFormats.length === 0}
                    className="bg-[var(--surface-2)] border border-[var(--surface-3)] rounded-lg px-2 py-1.5 font-mono text-xs text-white/80 outline-none hover:border-white/15 focus:border-[var(--accent)]/50 transition-colors disabled:opacity-40"
                  >
                    {encodableFormats.map((f) => (
                      <option key={f.name.toLowerCase()} value={f.name.toLowerCase()}>{f.name.toUpperCase()}</option>
                    ))}
                  </select>
                </div>

                <button
                  onClick={() => handle && handleTransform(handle, outputFormat, pipeline)}
                  disabled={!handle || uploading || processing}
                  className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-[var(--accent)] text-white font-mono text-xs font-semibold hover:opacity-90 active:scale-95 disabled:opacity-40 disabled:cursor-not-allowed transition-all duration-150"
                >
                  {processing
                    ? <><span className="w-3 h-3 border border-white/60 border-t-transparent rounded-full animate-spin" />running</>
                    : <>run</>
                  }
                </button>

                <button
                  onClick={() => setPipelineOpen((v) => !v)}
                  className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg font-mono text-xs border transition-all duration-150 ${
                    pipelineOpen
                      ? 'border-[var(--accent)]/40 text-[var(--accent)] bg-[var(--accent)]/8'
                      : 'border-[var(--surface-3)] text-white/35 hover:text-white/60 hover:border-white/15'
                  }`}
                >
                  <span>pipeline</span>
                  {pipeline.length > 0 && (
                    <span className="w-4 h-4 rounded bg-[var(--accent)]/20 text-[var(--accent)] text-[10px] flex items-center justify-center">{pipeline.length}</span>
                  )}
                </button>

                {result && resultUrl && (
                  <a href={resultUrl} download
                    className="ml-auto flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-[var(--surface-2)] border border-[var(--surface-3)] text-xs font-mono text-[#888898] hover:text-white transition-colors duration-150"
                  >save</a>
                )}
                {uploading && <span className="text-xs font-mono text-[#444456] animate-pulse">uploading...</span>}
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  )
}

function AudioPlayer({ label, src, accent = false }: { label: string; src: string; accent?: boolean }) {
  return (
    <div className="w-full max-w-xl space-y-2">
      <div className={`text-[10px] font-mono uppercase tracking-widest ${accent ? 'text-[var(--accent)]' : 'text-[#555568]'}`}>{label}</div>
      <audio src={src} controls className="w-full rounded-xl" />
    </div>
  )
}
