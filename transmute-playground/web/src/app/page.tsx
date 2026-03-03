'use client'

import { useState, useCallback, useEffect, useRef, useMemo } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import Navbar from '@/components/Navbar'
import FileDropZone from '@/components/FileDropZone'
import ToolPalette from '@/components/ToolPalette'
import EditorToolOptions from '@/components/EditorToolOptions'
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
  MediaDomain,
} from '@/lib/types'
import type { HistoryEntry } from '@/lib/editorTypes'

// --- Canvas media renderer ----------------------------------------------------

function CanvasMedia({ src, domain }: { src: string; domain: MediaDomain }) {
  if (domain === 'AUDIO') {
    return (
      <div className="w-full max-w-md px-6">
        <audio src={src} controls className="w-full rounded-xl" />
      </div>
    )
  }
  if (domain === 'VIDEO') {
    return (
      <video
        src={src}
        controls
        className="max-w-full max-h-full object-contain"
        style={{ maxHeight: 'calc(100dvh - 180px)' }}
      />
    )
  }
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={src}
      alt=""
      draggable={false}
      className="max-w-full max-h-full object-contain select-none"
      style={{ maxHeight: 'calc(100dvh - 180px)' }}
    />
  )
}

export default function TransformPage() {
  // Upload state
  const [file, setFile] = useState<File | null>(null)
  const [localOriginalUrl, setLocalOriginalUrl] = useState<string | null>(null)
  const [handle, setHandle] = useState<FileHandle | null>(null)
  const [inspection, setInspection] = useState<InspectResult | null>(null)
  const [uploading, setUploading] = useState(false)

  // Remote data
  const [formats, setFormats] = useState<FormatInfo[]>([])
  const [transforms, setTransforms] = useState<TransformInfo[]>([])
  const [outputFormat, setOutputFormat] = useState('')

  // Editor state
  const [history, setHistory] = useState<HistoryEntry[]>([])
  const [headHandle, setHeadHandle] = useState<string | null>(null)
  const [activeTool, setActiveTool] = useState<TransformInfo | null>(null)
  const [activeParams, setActiveParams] = useState<Record<string, string>>({})
  const [previewHandle, setPreviewHandle] = useState<string | null>(null)

  // Canvas UI state
  const [compareMode, setCompareMode] = useState(false)
  const [comparePos, setComparePos] = useState(50)
  const [processing, setProcessing] = useState(false)
  const [previewing, setPreviewing] = useState(false)

  const localUrlRef = useRef<string | null>(null)
  const previewTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const previewSeqRef = useRef(0)
  const compareDragging = useRef(false)
  const compareContainerRef = useRef<HTMLDivElement>(null)

  const toast = useToast()

  const activeParamState = useMemo(() => {
    if (!activeTool) return { ok: false, missing: [] as string[] }
    const missing: string[] = []
    for (const p of activeTool.parameters ?? []) {
      if (!p.required) continue
      const raw = activeParams[p.name] ?? ''
      if (raw.trim() === '') missing.push(p.name)
    }
    return { ok: missing.length === 0, missing }
  }, [activeTool, activeParams])

  // Derived
  const domain: MediaDomain = inspection?.domain ?? 'IMAGE'
  const originalHandle = handle?.handle ?? null
  const committedHandle = headHandle ?? originalHandle
  const currentHandle = committedHandle
  const encodableFormats = formats.filter((f) => f.domain === domain && f.canEncode)
  const canDownload = history.length > 0 && headHandle !== null

  const compareAvailable = useMemo(() => {
    if (previewHandle) return true // committed -> draft
    if (!originalHandle || !committedHandle) return false
    return originalHandle !== committedHandle // original -> committed
  }, [previewHandle, originalHandle, committedHandle])

  // -- On mount -------------------------------------------------------------
  useEffect(() => {
    fetchFormats().then(setFormats).catch(() => {})
    fetchTransforms().then(setTransforms).catch(() => {})
  }, [])

  // Ensure output format always maps to an encodable option when available.
  useEffect(() => {
    if (encodableFormats.length === 0) return
    const normalized = outputFormat.toLowerCase()
    const has = encodableFormats.some((f) => f.name.toLowerCase() === normalized)
    if (!normalized || !has) setOutputFormat(encodableFormats[0].name.toLowerCase())
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [encodableFormats])

  // If we lose the ability to compare, exit compare mode.
  useEffect(() => {
    if (!compareAvailable && compareMode) setCompareMode(false)
  }, [compareAvailable, compareMode])

  useEffect(() => {
    return () => {
      if (localUrlRef.current) URL.revokeObjectURL(localUrlRef.current)
    }
  }, [])

  // -- File drop ------------------------------------------------------------
  const handleFile = useCallback(
    async (dropped: File) => {
      setFile(dropped)
      setHistory([])
      setHeadHandle(null)
      setActiveTool(null)
      setActiveParams({})
      setPreviewHandle(null)
      setCompareMode(false)
      setHandle(null)
      setInspection(null)
      setOutputFormat('')

      if (localUrlRef.current) URL.revokeObjectURL(localUrlRef.current)
      const blobUrl = URL.createObjectURL(dropped)
      localUrlRef.current = blobUrl
      setLocalOriginalUrl(blobUrl)

      setUploading(true)
      try {
        const h = await uploadFile(dropped)
        setHandle(h)
        setHeadHandle(h.handle)
        const insp = await inspectFile(h.handle)
        setInspection(insp)
        setOutputFormat(insp.format.toLowerCase())
      } catch (e: unknown) {
        toast.error(e instanceof Error ? e.message : 'Upload failed')
      } finally {
        setUploading(false)
      }
    },
    [toast],
  )

  // -- Tool selection --------------------------------------------------------
  const handleSelectTool = useCallback(
    (tool: TransformInfo) => {
      if (activeTool?.id === tool.id) {
        setActiveTool(null)
        setActiveParams({})
        setPreviewHandle(null)
        return
      }
      const defaults: Record<string, string> = {}
      for (const p of tool.parameters ?? []) {
        if (typeof p.default === 'string') defaults[p.name] = p.default
      }
      setActiveTool(tool)
      setActiveParams(defaults)
      setPreviewHandle(null)
    },
    [activeTool],
  )

  // -- Live preview (debounced) ----------------------------------------------
  useEffect(() => {
    if (!activeTool || !currentHandle) return
    if (!activeParamState.ok) {
      setPreviewHandle(null)
      return
    }
    if (previewTimerRef.current) clearTimeout(previewTimerRef.current)
    previewTimerRef.current = setTimeout(async () => {
      const seq = ++previewSeqRef.current
      const params: Record<string, string> = Object.fromEntries(
        Object.entries(activeParams).filter(([, v]) => v !== ''),
      )
      setPreviewing(true)
      setPreviewHandle(null)
      try {
        const res = await executeTransform({
          fileHandle: currentHandle,
          outputFormat,
          pipeline: [{ transformId: activeTool.id, parameters: params }],
        })
        if (seq === previewSeqRef.current) setPreviewHandle(res.resultHandle)
      } catch {
        // Silent
      } finally {
        if (seq === previewSeqRef.current) setPreviewing(false)
      }
    }, 300)
    return () => {
      if (previewTimerRef.current) clearTimeout(previewTimerRef.current)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeTool, activeParams, currentHandle, outputFormat, activeParamState.ok])

  // -- Apply -----------------------------------------------------------------
  const handleApply = useCallback(async () => {
    if (!activeTool || !currentHandle) return
    if (!activeParamState.ok) return
    setProcessing(true)
    try {
      const params: Record<string, string> = Object.fromEntries(
        Object.entries(activeParams).filter(([, v]) => v !== ''),
      )
      const res = await executeTransform({
        fileHandle: currentHandle,
        outputFormat,
        pipeline: [{ transformId: activeTool.id, parameters: params }],
      })
      const entry: HistoryEntry = {
        id: `${activeTool.id}-${Date.now()}`,
        tool: activeTool,
        params,
        inputHandle: currentHandle,
        resultHandle: res.resultHandle,
      }
      setHistory((prev) => [...prev, entry])
      setHeadHandle(res.resultHandle)
      setActiveTool(null)
      setActiveParams({})
      setPreviewHandle(null)
      setCompareMode(false)
      toast.success(`Applied: ${activeTool.id}`, `${formatBytes(res.fileSize)} · ${res.durationMs}ms`)
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : 'Transform failed')
    } finally {
      setProcessing(false)
    }
  }, [activeTool, activeParams, currentHandle, outputFormat, toast, activeParamState.ok])

  // -- Reset params to defaults ----------------------------------------------
  const handleReset = useCallback(() => {
    if (!activeTool) return
    const defaults: Record<string, string> = {}
    for (const p of activeTool.parameters ?? []) {
      if (typeof p.default === 'string') defaults[p.name] = p.default
    }
    setActiveParams(defaults)
    setPreviewHandle(null)
  }, [activeTool])

  // -- Undo ------------------------------------------------------------------
  const handleUndo = useCallback(() => {
    setHistory((prev) => {
      if (prev.length === 0) return prev
      const last = prev[prev.length - 1]
      setHeadHandle(last.inputHandle)
      return prev.slice(0, -1)
    })
    setActiveTool(null)
    setActiveParams({})
    setPreviewHandle(null)
    setCompareMode(false)
  }, [])

  // -- Clear everything ------------------------------------------------------
  const reset = useCallback(() => {
    setFile(null)
    setHandle(null)
    setInspection(null)
    setLocalOriginalUrl(null)
    setHistory([])
    setHeadHandle(null)
    setActiveTool(null)
    setActiveParams({})
    setPreviewHandle(null)
    setCompareMode(false)
    setOutputFormat('')
    if (localUrlRef.current) {
      URL.revokeObjectURL(localUrlRef.current)
      localUrlRef.current = null
    }
  }, [])

  // -- Keyboard shortcuts ----------------------------------------------------
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement | null
      const inFormField =
        !!target &&
        (target.tagName === 'INPUT' ||
          target.tagName === 'TEXTAREA' ||
          target.tagName === 'SELECT' ||
          target.isContentEditable)

      if (e.key === 'c' && !e.ctrlKey && !e.metaKey && compareAvailable) {
        if (inFormField) return
        setCompareMode((v) => !v)
      }
      if (e.key === 'Escape') {
        setActiveTool(null)
        setActiveParams({})
        setPreviewHandle(null)
      }
      if ((e.ctrlKey || e.metaKey) && e.key === 'z') {
        if (inFormField) return
        e.preventDefault()
        handleUndo()
      }
      if ((e.ctrlKey || e.metaKey) && e.key === 's' && canDownload && headHandle) {
        e.preventDefault()
        const a = document.createElement('a')
        a.href = fileUrl(headHandle)
        a.download = ''
        a.click()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [compareAvailable, canDownload, headHandle, handleUndo])

  // -- Compare drag ----------------------------------------------------------
  const onCompareDragStart = useCallback(() => { compareDragging.current = true }, [])
  const onCompareDrag = useCallback((e: React.PointerEvent) => {
    if (!compareDragging.current || !compareContainerRef.current) return
    const rect = compareContainerRef.current.getBoundingClientRect()
    const pct = ((e.clientX - rect.left) / rect.width) * 100
    setComparePos(Math.min(92, Math.max(8, pct)))
  }, [])
  const onCompareDragEnd = useCallback(() => { compareDragging.current = false }, [])

  // -- Source URLs -----------------------------------------------------------
  const singleSrc = previewHandle
    ? fileUrl(previewHandle)
    : committedHandle
      ? fileUrl(committedHandle)
      : localOriginalUrl

  const { beforeSrc, afterSrc } = useMemo(() => {
    // Draft compare: committed -> preview
    if (previewHandle && committedHandle) {
      return {
        beforeSrc: fileUrl(committedHandle),
        afterSrc: fileUrl(previewHandle),
      }
    }

    // Result compare: original -> committed
    if (originalHandle && committedHandle && originalHandle !== committedHandle) {
      return {
        beforeSrc: fileUrl(originalHandle),
        afterSrc: fileUrl(committedHandle),
      }
    }

    // Fallback (no compare available)
    const src = committedHandle ? fileUrl(committedHandle) : localOriginalUrl
    return { beforeSrc: src, afterSrc: src }
  }, [previewHandle, committedHandle, originalHandle, localOriginalUrl])

  const compareLabels = useMemo(() => {
    if (previewHandle) return { before: 'current', after: 'draft' }
    if (originalHandle && committedHandle && originalHandle !== committedHandle) {
      return { before: 'original', after: 'result' }
    }
    return { before: 'before', after: 'after' }
  }, [previewHandle, originalHandle, committedHandle])

  // -- Render ----------------------------------------------------------------
  return (
    <div className="h-dvh flex flex-col overflow-hidden">
      <Navbar />

      <div className="flex flex-col flex-1 overflow-hidden min-h-0">
        <AnimatePresence mode="wait">
          {/* -- Empty state ----------------------------------------------- */}
          {!localOriginalUrl ? (
            <motion.div
              key="empty"
              initial={{ opacity: 0, scale: 0.97 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.97 }}
              transition={{ duration: 0.18 }}
              className="flex-1 flex flex-col items-center justify-center px-6 pb-16"
            >
              <h1 className="font-display text-4xl sm:text-5xl text-white tracking-tight mb-3">
                Transform
              </h1>
              <p className="text-[#555568] text-sm mb-10 max-w-sm text-center">
                Drop a media file to apply transforms and preview results live.
              </p>
              <div className="w-full max-w-lg">
                <FileDropZone onFile={handleFile} disabled={false} />
              </div>
            </motion.div>
          ) : (
            /* -- Editor layout -------------------------------------------- */
            <motion.div
              key="editor"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.15 }}
              className="flex-1 flex overflow-hidden min-h-0"
            >
              {/* -- Left: Tool Palette ------------------------------------ */}
              <div className="w-[185px] shrink-0 border-r border-[var(--surface-3)]/50 bg-[var(--surface-1)] flex flex-col overflow-hidden">
                <div className="shrink-0 px-3 pt-3 pb-2 border-b border-[var(--surface-3)]/30">
                  <span className="text-[8px] font-mono uppercase tracking-[0.2em] text-[#28283a]">
                    Tools
                  </span>
                </div>
                {inspection ? (
                  <ToolPalette
                    transforms={transforms}
                    domain={domain}
                    activeTool={activeTool}
                    history={history}
                    onSelect={handleSelectTool}
                  />
                ) : (
                  <div className="flex-1 flex items-center justify-center">
                    <span className="text-[9px] font-mono text-[#28283a] animate-pulse">
                      loading...
                    </span>
                  </div>
                )}
              </div>

              {/* -- Centre: Canvas ---------------------------------------- */}
              <div className="flex-1 flex flex-col overflow-hidden min-w-0">
                {/* Info + toolbar */}
                <div className="shrink-0 flex items-center gap-2 px-3 pt-2 pb-2 border-b border-[var(--surface-3)]/30 bg-[var(--surface-1)]">
                  <div className="flex items-center gap-1.5 min-w-0">
                    <span className="w-[5px] h-[5px] rounded-full bg-[var(--accent)] shrink-0" />
                    <span className="font-mono text-[11px] text-[#909098] truncate max-w-[160px]">
                      {file?.name}
                    </span>
                    {inspection && (
                      <span className="font-mono text-[9px] text-[var(--accent)] uppercase shrink-0 tracking-wide">
                        {inspection.format}
                      </span>
                    )}
                    {uploading && (
                      <span className="font-mono text-[9px] text-[#3a3a50] animate-pulse shrink-0">
                        uploading...
                      </span>
                    )}
                  </div>

                  <span className="flex-1" />

                  <div className="flex items-center gap-1 shrink-0">
                    <button
                      onClick={() => setCompareMode((v) => !v)}
                      title="Compare (C)"
                      disabled={!compareAvailable}
                      className={`px-2 py-1 rounded font-mono text-[9.5px] border transition-all duration-100 disabled:opacity-25 disabled:cursor-not-allowed ${
                        compareMode
                          ? 'bg-[var(--accent)]/12 border-[var(--accent)]/35 text-[var(--accent)]'
                          : 'border-[var(--surface-3)] text-[#8e8ea8] hover:text-white hover:border-[#444458]'
                      }`}
                    >
                      ⊟ compare
                    </button>

                    <button
                      onClick={handleUndo}
                      disabled={history.length === 0}
                      title="Undo (Ctrl+Z)"
                      className="px-2 py-1 rounded font-mono text-[9.5px] border border-[var(--surface-3)] text-[#8e8ea8] hover:text-white hover:border-[#444458] transition-all duration-100 disabled:opacity-25 disabled:cursor-not-allowed"
                    >
                      ↩
                    </button>

                    {encodableFormats.length > 0 && (
                      <div className="flex items-center gap-1">
                        <span className="text-[8.5px] font-mono text-[#28283a] uppercase tracking-wider">as</span>
                        <select
                          value={outputFormat}
                          onChange={(e) => {
                            // Force a new preview immediately (avoids stale preview + compare sources)
                            previewSeqRef.current++
                            setPreviewHandle(null)
                            setCompareMode(false)
                            setOutputFormat(e.target.value)
                          }}
                          className="bg-[var(--surface-2)] border border-[var(--surface-3)] rounded px-1.5 py-0.5 font-mono text-[9.5px] text-[#8e8ea8] outline-none hover:border-[#333348] transition-colors cursor-pointer"
                        >
                          {encodableFormats.map((f) => (
                            <option key={f.name.toLowerCase()} value={f.name.toLowerCase()}>
                              {f.name.toUpperCase()}
                            </option>
                          ))}
                        </select>
                      </div>
                    )}

                    {canDownload && headHandle && (
                      <a
                        href={fileUrl(headHandle)}
                        download
                        className="px-2 py-1 rounded font-mono text-[9.5px] border border-[var(--surface-3)] text-[#8e8ea8] hover:text-white hover:border-[#444458] transition-all duration-100 flex items-center gap-1"
                      >
                        ↓ save
                      </a>
                    )}

                    <button
                      onClick={reset}
                      className="px-1.5 py-1 rounded font-mono text-[9.5px] text-[#28283a] hover:text-[#5a5a68] transition-colors ml-0.5"
                    >
                      ×
                    </button>
                  </div>
                </div>

                {/* Canvas */}
                <div
                  ref={compareContainerRef}
                  className="flex-1 relative overflow-hidden bg-[var(--surface-0)] min-h-0"
                  onPointerMove={onCompareDrag}
                  onPointerUp={onCompareDragEnd}
                  onPointerLeave={onCompareDragEnd}
                >
                  {/* Subtle checker (shows transparency) */}
                  <div
                    className="absolute inset-0 pointer-events-none opacity-[0.025]"
                    style={{
                      backgroundImage:
                        'repeating-conic-gradient(#888 0% 25%, transparent 0% 50%)',
                      backgroundSize: '24px 24px',
                    }}
                  />

                  <AnimatePresence>
                    {compareMode && beforeSrc && afterSrc ? (
                      <motion.div
                        key="compare"
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0 }}
                        className="absolute inset-0 flex select-none"
                      >
                        {/* Before */}
                        <div
                          className="h-full relative flex items-center justify-center overflow-hidden"
                          style={{ width: `${comparePos}%` }}
                        >
                          <CanvasMedia src={beforeSrc} domain={domain} />
                          <span className="absolute top-3 left-3 px-1.5 py-0.5 rounded text-[8px] font-mono uppercase tracking-widest text-white/25 bg-black/25 backdrop-blur-sm">
                            {compareLabels.before}
                          </span>
                        </div>

                        {/* Divider */}
                        <div
                          className="w-px relative shrink-0 z-10 bg-[var(--accent)]/50 cursor-col-resize"
                          onPointerDown={(e) => {
                            e.preventDefault()
                            ;(e.target as HTMLElement).setPointerCapture(e.pointerId)
                            onCompareDragStart()
                          }}
                        >
                          <div className="absolute top-1/2 -translate-y-1/2 -translate-x-1/2 w-[18px] h-10 rounded-full bg-[var(--surface-2)] border border-[var(--accent)]/35 flex items-center justify-center pointer-events-none">
                            <span className="text-[7px] text-[var(--accent)]/50">⇔</span>
                          </div>
                        </div>

                        {/* After */}
                        <div className="flex-1 h-full relative flex items-center justify-center overflow-hidden">
                          <CanvasMedia src={afterSrc} domain={domain} />
                          <span className="absolute top-3 right-3 px-1.5 py-0.5 rounded text-[8px] font-mono uppercase tracking-widest text-[var(--accent)]/50 bg-black/25 backdrop-blur-sm">
                            {compareLabels.after}
                          </span>
                          {previewing && (
                            <div className="absolute bottom-3 right-3 flex items-center gap-1 px-1.5 py-0.5 rounded text-[8px] font-mono text-white/20 bg-black/20 backdrop-blur-sm">
                              <span className="w-1 h-1 rounded-full bg-[var(--accent)] animate-pulse" />
                              live
                            </div>
                          )}
                        </div>
                      </motion.div>
                    ) : (
                      <motion.div
                        key="single"
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0 }}
                        className="absolute inset-0 flex items-center justify-center"
                      >
                        {singleSrc && <CanvasMedia src={singleSrc} domain={domain} />}
                        {!singleSrc && !uploading && (
                          <p className="text-[9px] font-mono text-[#28283a]">no preview</p>
                        )}
                        {uploading && (
                          <p className="text-[9px] font-mono text-[#3a3a50] animate-pulse">uploading...</p>
                        )}
                        {previewing && (
                          <div className="absolute bottom-3 right-3 flex items-center gap-1 px-1.5 py-0.5 rounded text-[8px] font-mono text-white/20 bg-black/20 backdrop-blur-sm">
                            <span className="w-1 h-1 rounded-full bg-[var(--accent)] animate-pulse" />
                            previewing
                          </div>
                        )}
                      </motion.div>
                    )}
                  </AnimatePresence>
                </div>
              </div>

              {/* -- Right: Tool Options + History ------------------------- */}
              <div className="w-[250px] shrink-0 border-l border-[var(--surface-3)]/50 bg-[var(--surface-1)] flex flex-col overflow-hidden">
                <div className="shrink-0 px-3 pt-3 pb-2 border-b border-[var(--surface-3)]/30">
                  <AnimatePresence mode="wait">
                    <motion.span
                      key={activeTool?.id ?? 'idle'}
                      initial={{ opacity: 0, y: 3 }}
                      animate={{ opacity: 1, y: 0 }}
                      exit={{ opacity: 0, y: -3 }}
                      transition={{ duration: 0.1 }}
                      className="text-[8px] font-mono uppercase tracking-[0.2em] text-[#28283a] block"
                    >
                      {activeTool ? activeTool.id : 'Options'}
                    </motion.span>
                  </AnimatePresence>
                </div>
                <div className="flex-1 min-h-0">
                  <EditorToolOptions
                    activeTool={activeTool}
                    params={activeParams}
                    onChange={setActiveParams}
                    onApply={handleApply}
                    onReset={handleReset}
                    history={history}
                    onUndo={handleUndo}
                    canUndo={history.length > 0}
                    processing={processing}
                    previewing={previewing}
                    canApply={activeParamState.ok}
                    applyHint={activeParamState.missing.join(', ')}
                  />
                </div>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  )
}
