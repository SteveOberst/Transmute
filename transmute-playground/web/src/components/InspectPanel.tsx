'use client'

import { useState, useMemo } from 'react'
import type { InspectResult, MediaMetadata, MediaStructure } from '@/lib/types'
import { motion, AnimatePresence } from 'framer-motion'
import { formatBytes } from '@/lib/utils'

interface Props {
  result: InspectResult
}

// ===============================================================================
//  Main InspectPanel
// ===============================================================================

export default function InspectPanel({ result }: Props) {
  const domainClass =
    result.domain === 'AUDIO' ? 'chip-audio'
    : result.domain === 'VIDEO' ? 'chip-video'
    : 'chip-image'

  const hasStructure = !!result.structure
  const hasMetadata = !!result.metadata && result.metadata.length > 0

  const [activeTab, setActiveTab] = useState<'metadata' | 'structure'>(
    hasMetadata ? 'metadata' : 'structure',
  )

  const overview = useMemo(
    () => result.structure ? extractOverview(result.structure, result.domain) : null,
    [result.structure, result.domain],
  )

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      className="rounded-2xl border border-[var(--surface-3)] bg-[var(--surface-1)] overflow-hidden"
    >
      {/* -- Header bar ------------------------------------------------ */}
      <div className="flex items-center justify-between px-5 py-3 bg-[var(--surface-2)] border-b border-[var(--surface-3)]">
        <div className="flex items-center gap-3">
          <span className={`text-[10px] font-mono px-2 py-0.5 rounded ${domainClass}`}>
            {result.domain}
          </span>
          <span className="font-mono text-white text-sm font-medium uppercase">
            {result.format}
          </span>
        </div>
        <span className="font-mono text-xs text-[#666680]">
          {formatBytes(result.fileSize)}
        </span>
      </div>

      {/* -- Overview card --------------------------------------------- */}
      {overview && overview.length > 0 && (
        <OverviewCard items={overview} domain={result.domain} />
      )}

      {/* -- Tab bar --------------------------------------------------- */}
      {(hasStructure || hasMetadata) && (
        <div className="flex border-b border-[var(--surface-3)]">
          {hasMetadata && (
            <TabButton
              active={activeTab === 'metadata'}
              onClick={() => setActiveTab('metadata')}
              count={result.metadata!.length}
            >
              Metadata
            </TabButton>
          )}
          {hasStructure && (
            <TabButton
              active={activeTab === 'structure'}
              onClick={() => setActiveTab('structure')}
            >
              Structure
            </TabButton>
          )}
        </div>
      )}

      {/* -- Content --------------------------------------------------- */}
      <AnimatePresence mode="wait">
        {activeTab === 'structure' && hasStructure && (
          <motion.div
            key="structure"
            initial={{ opacity: 0, x: -8 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: 8 }}
            transition={{ duration: 0.15 }}
            className="p-5"
          >
            <div className="flex items-center gap-2 mb-2">
              <span className="text-xs text-[#666680] font-mono uppercase tracking-wider">
                Structure
              </span>
              <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-[var(--surface-2)] text-[#555568] border border-[var(--surface-3)]">
                {result.structure!.type}
              </span>
            </div>
            <div className="code-block max-h-96 overflow-y-auto text-xs p-3 rounded-xl bg-[var(--surface-2)] border border-[var(--surface-3)]">
              <JsonTree value={result.structure!.value} depth={0} />
            </div>
          </motion.div>
        )}

        {activeTab === 'metadata' && hasMetadata && (
          <motion.div
            key="metadata"
            initial={{ opacity: 0, x: 8 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: -8 }}
            transition={{ duration: 0.15 }}
            className="p-5"
          >
            <div className="space-y-4">
              {result.metadata!.map((meta, i) => (
                <motion.div
                  key={`${meta.type}-${i}`}
                  initial={{ opacity: 0, y: 12 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: i * 0.06, duration: 0.3 }}
                >
                  <MetadataBlock meta={meta} index={i} />
                </motion.div>
              ))}
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* -- No-data fallback ------------------------------------------ */}
      {!hasStructure && !hasMetadata && (
        <div className="p-5 text-center">
          <span className="text-[11px] font-mono text-[#444456] italic">
            No structure or metadata available for this format
          </span>
        </div>
      )}
    </motion.div>
  )
}

// ===============================================================================
//  Overview card - key file properties at a glance
// ===============================================================================

interface OverviewItem {
  label: string
  value: string
  accent?: boolean
}

function extractOverview(
  structure: MediaStructure,
  domain: string,
): OverviewItem[] {
  const v = structure.value
  const items: OverviewItem[] = []

  if (domain === 'IMAGE') {
    // JPEG hierarchical: frame.sofData.width/height
    const frame = v.frame as Record<string, unknown> | undefined
    const sofFromFrame = frame?.sofData as Record<string, unknown> | undefined
    // JPEG flat fallback: top-level sofData
    const sof = sofFromFrame ?? (v.sofData as Record<string, unknown> | undefined)
    if (sof) {
      if (sof.width && sof.height) {
        items.push({ label: 'Dimensions', value: `${sof.width} \u00D7 ${sof.height} px`, accent: true })
      }
      if (sof.precision) items.push({ label: 'Bit Depth', value: `${sof.precision}-bit` })
      const comps = sof.components as unknown[] | undefined
      if (comps) items.push({ label: 'Components', value: `${comps.length}` })
    }
    // JPEG: jfifHeader
    const jfif = v.jfifHeader as Record<string, unknown> | undefined
    if (jfif?.xDensity && jfif?.yDensity) {
      const units = jfif.densityUnits === 1 ? 'DPI' : jfif.densityUnits === 2 ? 'DPCM' : ''
      items.push({ label: 'Density', value: `${jfif.xDensity}\u00D7${jfif.yDensity} ${units}`.trim() })
    }
    // JPEG: frame info
    if (frame) {
      const sofMarker = frame.sofMarkerName as string | undefined
      if (sofMarker) items.push({ label: 'Frame Type', value: sofMarker })
      const scans = frame.scans as unknown[] | undefined
      if (scans && scans.length > 1) {
        items.push({ label: 'Scans', value: `${scans.length} (progressive)` })
      }
    }
    // PNG: ihdr
    const ihdr = v.ihdr as Record<string, unknown> | undefined
    if (ihdr) {
      if (ihdr.width && ihdr.height) {
        items.push({ label: 'Dimensions', value: `${ihdr.width} \u00D7 ${ihdr.height} px`, accent: true })
      }
      if (ihdr.bitDepth) items.push({ label: 'Bit Depth', value: `${ihdr.bitDepth}-bit` })
      if (ihdr.colorType !== undefined) {
        const ct = PNG_COLOR_TYPES[ihdr.colorType as number] ?? `Type ${ihdr.colorType}`
        items.push({ label: 'Color', value: ct })
      }
      if (ihdr.interlace) items.push({ label: 'Interlace', value: 'Adam7' })
    }
    const actl = v.actl as Record<string, unknown> | undefined
    if (actl?.numFrames) items.push({ label: 'Frames', value: `${actl.numFrames} (APNG)` })
    // BMP
    const dibHeader = v.dibHeader as Record<string, unknown> | undefined
    if (dibHeader) {
      if (dibHeader.width && dibHeader.height) {
        items.push({ label: 'Dimensions', value: `${dibHeader.width} \u00D7 ${dibHeader.height} px`, accent: true })
      }
      if (dibHeader.bitsPerPixel) items.push({ label: 'Bit Depth', value: `${dibHeader.bitsPerPixel}-bit` })
    }
    // GIF
    const screenDescriptor = v.screenDescriptor as Record<string, unknown> | undefined
    if (screenDescriptor) {
      if (screenDescriptor.width && screenDescriptor.height) {
        items.push({ label: 'Dimensions', value: `${screenDescriptor.width} \u00D7 ${screenDescriptor.height} px`, accent: true })
      }
    }
    // TIFF IFDs
    const ifds = v.ifds as unknown[] | undefined
    if (ifds && !sof && !ihdr) {
      items.push({ label: 'IFDs', value: `${ifds.length}` })
    }
    // ISO BMFF images (HEIF, AVIF)
    const ftyp = v.ftyp as Record<string, unknown> | undefined
    if (ftyp?.majorBrand) items.push({ label: 'Brand', value: String(ftyp.majorBrand) })
    const boxes = v.boxes as unknown[] | undefined
    if (boxes && !ihdr && !sof && !dibHeader) {
      items.push({ label: 'Top-level Boxes', value: `${boxes.length}` })
    }
  }

  if (domain === 'AUDIO') {
    // WAV
    if (v.sampleRate) items.push({ label: 'Sample Rate', value: formatHz(v.sampleRate as number), accent: true })
    if (v.channels) items.push({ label: 'Channels', value: channelLabel(v.channels as number) })
    if (v.bitsPerSample) items.push({ label: 'Bit Depth', value: `${v.bitsPerSample}-bit` })
    if (v.audioFormat !== undefined) {
      const af = WAV_FORMATS[v.audioFormat as number] ?? `0x${(v.audioFormat as number).toString(16)}`
      items.push({ label: 'Format', value: af })
    }
    if (v.dataBytesTotal) items.push({ label: 'Audio Data', value: formatBytes(v.dataBytesTotal as number) })
    // MP3
    const firstFrame = v.firstFrame as Record<string, unknown> | undefined
    if (firstFrame) {
      if (firstFrame.sampleRate) items.push({ label: 'Sample Rate', value: formatHz(firstFrame.sampleRate as number), accent: true })
      if (firstFrame.bitrate) items.push({ label: 'Bitrate', value: `${firstFrame.bitrate} kbps` })
      if (firstFrame.channelMode) items.push({ label: 'Channels', value: String(firstFrame.channelMode) })
      if (firstFrame.version) items.push({ label: 'MPEG', value: String(firstFrame.version) })
      if (firstFrame.layer) items.push({ label: 'Layer', value: String(firstFrame.layer) })
    }
    // FLAC
    const streamInfo = v.streamInfo as Record<string, unknown> | undefined
    if (streamInfo) {
      if (streamInfo.sampleRate) items.push({ label: 'Sample Rate', value: formatHz(streamInfo.sampleRate as number), accent: true })
      if (streamInfo.channels) items.push({ label: 'Channels', value: channelLabel(streamInfo.channels as number) })
      if (streamInfo.bitsPerSample) items.push({ label: 'Bit Depth', value: `${streamInfo.bitsPerSample}-bit` })
      if (streamInfo.totalSamples) {
        const dur = (streamInfo.totalSamples as number) / (streamInfo.sampleRate as number)
        items.push({ label: 'Duration', value: formatDuration(dur) })
      }
    }
    // AAC/OGG/Opus/M4A with ISO BMFF
    const ftypA = v.ftyp as Record<string, unknown> | undefined
    if (ftypA?.majorBrand) items.push({ label: 'Brand', value: String(ftypA.majorBrand) })
  }

  if (domain === 'VIDEO') {
    // ISO BMFF (MP4, MOV)
    const ftypV = v.ftyp as Record<string, unknown> | undefined
    if (ftypV?.majorBrand) items.push({ label: 'Brand', value: String(ftypV.majorBrand), accent: true })
    const compatBrands = ftypV?.compatibleBrands as string[] | undefined
    if (compatBrands && compatBrands.length > 0) {
      items.push({ label: 'Compatible', value: compatBrands.slice(0, 5).join(', ') })
    }
    const boxesV = v.boxes as unknown[] | undefined
    if (boxesV) items.push({ label: 'Top-level Boxes', value: `${boxesV.length}` })
    // RIFF (AVI)
    const riffChunks = v.chunks as unknown[] | undefined
    if (riffChunks) items.push({ label: 'RIFF Chunks', value: `${riffChunks.length}` })
    // EBML (MKV, WebM)
    const elements = v.elements as unknown[] | undefined
    if (elements) items.push({ label: 'EBML Elements', value: `${elements.length}` })
  }

  return items
}

const PNG_COLOR_TYPES: Record<number, string> = {
  0: 'Grayscale',
  2: 'RGB',
  3: 'Indexed',
  4: 'Grayscale+Alpha',
  6: 'RGBA',
}

const WAV_FORMATS: Record<number, string> = {
  1: 'PCM',
  3: 'IEEE Float',
  6: 'A-law',
  7: '\u03BC-law',
  0xFFFE: 'Extensible',
}

function formatHz(hz: number): string {
  if (hz >= 1000) return `${(hz / 1000).toFixed(hz % 1000 === 0 ? 0 : 1)} kHz`
  return `${hz} Hz`
}

function channelLabel(ch: number): string {
  if (ch === 1) return 'Mono'
  if (ch === 2) return 'Stereo'
  return `${ch}ch`
}

function formatDuration(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${s.toString().padStart(2, '0')}`
}

function OverviewCard({ items, domain }: { items: OverviewItem[]; domain: string }) {
  const accentColor =
    domain === 'AUDIO' ? '#38bdf8'
    : domain === 'VIDEO' ? '#34d399'
    : '#a78bfa'

  return (
    <div className="px-5 py-4 border-b border-[var(--surface-3)]">
      <div className="grid grid-cols-2 sm:grid-cols-3 gap-x-6 gap-y-3">
        {items.map((item, i) => (
          <motion.div
            key={item.label}
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.04, duration: 0.25 }}
            className="min-w-0"
          >
            <div className="text-[9px] font-mono uppercase tracking-[0.08em] text-[#555568] mb-0.5">
              {item.label}
            </div>
            <div
              className="text-[13px] font-mono font-medium truncate"
              style={{ color: item.accent ? accentColor : '#c4c4d4' }}
            >
              {item.value}
            </div>
          </motion.div>
        ))}
      </div>
    </div>
  )
}

// ===============================================================================
//  Tab button
// ===============================================================================

function TabButton({
  active,
  onClick,
  children,
  count,
}: {
  active: boolean
  onClick: () => void
  children: React.ReactNode
  count?: number
}) {
  return (
    <button
      onClick={onClick}
      className={`
        relative px-5 py-2.5 text-xs font-mono uppercase tracking-wider
        transition-colors duration-200
        ${active
          ? 'text-[var(--accent)]'
          : 'text-[#555568] hover:text-[#888898]'
        }
      `}
    >
      <span className="flex items-center gap-2">
        {children}
        {count !== undefined && (
          <span className={`
            text-[9px] px-1.5 py-0.5 rounded-full
            ${active
              ? 'bg-[var(--accent)]/15 text-[var(--accent)]'
              : 'bg-[var(--surface-2)] text-[#555568]'
            }
          `}>
            {count}
          </span>
        )}
      </span>
      {active && (
        <motion.div
          layoutId="tab-indicator"
          className="absolute bottom-0 left-0 right-0 h-[2px] bg-[var(--accent)]"
          transition={{ type: 'spring', stiffness: 500, damping: 35 }}
        />
      )}
    </button>
  )
}

// ===============================================================================
//  Metadata type registry - maps type discriminator to renderer
// ===============================================================================

const METADATA_RENDERERS: Record<string, {
  label: string
  icon: string
  color: string
  render: (value: Record<string, unknown>) => React.ReactNode
}> = {
  'transmute.exif': {
    label: 'EXIF',
    icon: '\u{1F4F7}',
    color: '#e879f9',
    render: renderExif,
  },
  'transmute.xmp': {
    label: 'XMP',
    icon: '\u{1F4CB}',
    color: '#fb923c',
    render: renderXmp,
  },
  'transmute.icc': {
    label: 'ICC Profile',
    icon: '\u{1F3A8}',
    color: '#4ade80',
    render: renderIcc,
  },
  'transmute.id3v1': {
    label: 'ID3v1',
    icon: '\u{1F3B5}',
    color: '#38bdf8',
    render: renderId3v1,
  },
  'transmute.id3v2': {
    label: 'ID3v2',
    icon: '\u{1F3B6}',
    color: '#818cf8',
    render: renderId3v2,
  },
  'transmute.png-text': {
    label: 'PNG Text',
    icon: '\u{1F4DD}',
    color: '#f472b6',
    render: renderPngText,
  },
  'transmute.vorbis-comment': {
    label: 'Vorbis Comment',
    icon: '\u{1F50A}',
    color: '#a78bfa',
    render: renderVorbisComment,
  },
  'transmute.riff-info': {
    label: 'RIFF INFO',
    icon: '\u{1F39E}',
    color: '#34d399',
    render: renderRiffInfo,
  },
  'transmute.itunes': {
    label: 'iTunes',
    icon: '\u{1F34E}',
    color: '#fb7185',
    render: renderItunes,
  },
  'transmute.matroska-tags': {
    label: 'Matroska Tags',
    icon: '\u{1F3AC}',
    color: '#fbbf24',
    render: renderMatroskaTags,
  },
}

// ===============================================================================
//  MetadataBlock - dispatches to the right renderer
// ===============================================================================

function MetadataBlock({ meta, index }: { meta: MediaMetadata; index: number }) {
  const [expanded, setExpanded] = useState(index < 3)
  const renderer = METADATA_RENDERERS[meta.type]
  const label = renderer?.label ?? meta.type
  const icon = renderer?.icon ?? '\u{1F4C4}'
  const color = renderer?.color ?? '#888898'

  return (
    <div
      className="rounded-xl border overflow-hidden transition-colors"
      style={{ borderColor: expanded ? `${color}22` : 'var(--surface-3)' }}
    >
      {/* Block header */}
      <button
        onClick={() => setExpanded(e => !e)}
        className="w-full flex items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-[var(--surface-2)]/60"
        style={{ background: expanded ? `${color}06` : undefined }}
      >
        <span className="text-base leading-none">{icon}</span>
        <span
          className="text-[11px] font-mono font-medium tracking-wide uppercase"
          style={{ color }}
        >
          {label}
        </span>
        <span className="ml-auto text-[10px] font-mono text-[#444456]">
          {expanded ? '\u25BE' : '\u25B8'}
        </span>
      </button>

      {/* Block content */}
      <AnimatePresence initial={false}>
        {expanded && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.2 }}
            className="overflow-hidden"
          >
            <div className="px-4 pb-4 pt-1">
              {renderer
                ? renderer.render(meta.value)
                : <FallbackRenderer value={meta.value} />
              }
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

// ===============================================================================
//  Type-specific metadata renderers
// ===============================================================================

/* -- EXIF --------------------------------------------------------------- */

/** Human-friendly IFD section names. */
const IFD_LABELS: Record<string, string> = {
  ifd0: 'IFD0 (Main Image)',
  exifIfd: 'EXIF IFD',
  gpsIfd: 'GPS IFD',
  interopIfd: 'Interop IFD',
  ifd1: 'IFD1 (Thumbnail)',
}

/**
 * Extract a human-readable display string from a serialized ExifValue
 * (kotlinx.serialization sealed class with type discriminator).
 *
 * Handles: Text, Integers, Rationals, Floats, Blob
 */
function formatExifValue(val: unknown): string {
  if (val === null || val === undefined) return '\u2014'
  if (typeof val === 'string') return val
  if (typeof val === 'number') return String(val)
  if (typeof val !== 'object') return String(val)

  const obj = val as Record<string, unknown>

  // ExifValue.Text -> { type: "...", value: "Canon" }
  if (typeof obj.value === 'string') return obj.value

  // ExifValue.Integers / Floats / Rationals -> { type: "...", values: [...] }
  if (Array.isArray(obj.values)) {
    return (obj.values as unknown[]).map((v: unknown) => {
      if (typeof v === 'object' && v !== null) {
        const r = v as Record<string, unknown>
        // ExifRational: { numerator, denominator }
        if (r.numerator !== undefined && r.denominator !== undefined) {
          return r.denominator === 1 ? String(r.numerator) : `${r.numerator}/${r.denominator}`
        }
        return JSON.stringify(v)
      }
      return String(v)
    }).join(', ')
  }

  // ExifValue.Blob -> { type: "...", sizeBytes: 1024 }
  if (obj.sizeBytes !== undefined) return `[${obj.sizeBytes} bytes]`

  return JSON.stringify(val)
}

/**
 * Adaptive EXIF renderer — dynamically discovers IFD sections and entries
 * from whatever the backend returns, without hard-coding field expectations.
 */
function renderExif(value: Record<string, unknown>): React.ReactNode {
  const byteOrder = value.byteOrder as string | undefined

  // Dynamically discover IFD sections (objects with an `entries` array)
  const ifdSections: Array<{ key: string; label: string; entries: Array<Record<string, unknown>> }> = []
  const scalarProps: Array<{ label: string; value: string }> = []

  for (const [key, val] of Object.entries(value)) {
    if (val === null || val === undefined) continue
    if (typeof val === 'object' && !Array.isArray(val)) {
      const obj = val as Record<string, unknown>
      if (Array.isArray(obj.entries) && obj.entries.length > 0) {
        ifdSections.push({
          key,
          label: IFD_LABELS[key] ?? humanize(key),
          entries: obj.entries as Array<Record<string, unknown>>,
        })
      } else if (key !== 'byteOrder') {
        scalarProps.push({ label: humanize(key), value: JSON.stringify(val) })
      }
    } else if (key !== 'byteOrder' && typeof val !== 'object') {
      scalarProps.push({ label: humanize(key), value: String(val) })
    }
  }

  const isEmpty = ifdSections.length === 0 && scalarProps.length === 0
  if (isEmpty) return <FallbackRenderer value={value} />

  // Best-effort hero: scan all IFD entries for camera summary tags
  const allEntries = ifdSections.flatMap(s => s.entries)
  const findTag = (name: string) => {
    const entry = allEntries.find(
      e => (e.tagName as string)?.toLowerCase() === name.toLowerCase(),
    )
    return entry ? formatExifValue(entry.value) : undefined
  }
  const make = findTag('Make')
  const model = findTag('Model')
  const fNumber = findTag('FNumber')
  const exposure = findTag('ExposureTime')
  const iso = findTag('ISOSpeedRatings') ?? findTag('PhotographicSensitivity')

  return (
    <div className="space-y-3">
      {/* Hero: camera quick info (best-effort) */}
      {(make || model) && (
        <div className="flex items-start gap-3 pb-2 border-b border-[var(--surface-3)]/40">
          <div className="w-10 h-10 rounded-lg bg-[#e879f9]/10 flex items-center justify-center text-lg shrink-0">
            {'\u{1F4F7}'}
          </div>
          <div className="min-w-0 flex-1">
            <div className="text-sm text-white font-medium truncate">
              {[make, model].filter(Boolean).join(' ')}
            </div>
            <div className="flex items-center gap-2 mt-0.5 text-[11px] text-[#888898] font-mono">
              {fNumber != null && <span>\u0192/{fNumber}</span>}
              {exposure != null && <span>{exposure}s</span>}
              {iso != null && <span>ISO {iso}</span>}
            </div>
          </div>
        </div>
      )}

      {byteOrder && (
        <div className="text-[10px] font-mono text-[#555568]">
          Byte Order: <span className="text-[#888898]">{byteOrder.replace(/_/g, ' ')}</span>
        </div>
      )}

      {/* IFD sections — rendered dynamically based on what the backend returned */}
      {ifdSections.map(section => (
        <CollapsibleSection
          key={section.key}
          title={`${section.label} (${section.entries.length})`}
          defaultOpen={section.key === 'ifd0' || section.key === 'exifIfd'}
        >
          <MetadataTable
            rows={section.entries.map(entry => ({
              label: (entry.tagName as string) ?? `Tag 0x${((entry.tag as number) ?? 0).toString(16).toUpperCase().padStart(4, '0')}`,
              value: formatExifValue(entry.value),
              badge: entry.type as string | undefined,
            }))}
            compact
          />
        </CollapsibleSection>
      ))}

      {/* Any remaining scalar properties */}
      {scalarProps.length > 0 && (
        <CollapsibleSection title="Properties" defaultOpen={false}>
          <MetadataTable rows={scalarProps} compact />
        </CollapsibleSection>
      )}
    </div>
  )
}

/* -- XMP ---------------------------------------------------------------- */

function renderXmp(value: Record<string, unknown>): React.ReactNode {
  const root = value.root as Record<string, unknown> | undefined
  if (!root) return <FallbackRenderer value={value} />

  return (
    <div className="space-y-1">
      <XmpNodeRenderer node={root} depth={0} />
    </div>
  )
}

/**
 * Unwrap a polymorphic XmpNode serialized by kotlinx.serialization.
 *
 * XmpNode.Element -> { type: "...", element: { namespace, name, attributes, children } }
 * XmpNode.Text    -> { type: "...", content: "..." }
 *
 * The root XmpElement is NOT wrapped, so this is safe for both cases.
 */
function unwrapXmpNode(node: Record<string, unknown>): Record<string, unknown> {
  if (node.element && typeof node.element === 'object') {
    return node.element as Record<string, unknown>
  }
  return node
}

function XmpNodeRenderer({ node: rawNode, depth }: { node: Record<string, unknown>; depth: number }) {
  // Unwrap polymorphic XmpNode wrapper if present
  const node = unwrapXmpNode(rawNode)

  const name = node.name as string | undefined
  const ns = node.namespace as string | undefined
  const rawChildren = node.children as Array<Record<string, unknown>> | undefined
  const content = (rawNode.content as string | undefined) ?? (node.content as string | undefined)
  const attrs = node.attributes as Array<Record<string, unknown>> | undefined
  const [open, setOpen] = useState(depth < 3)

  // Text node
  if (content !== undefined && !rawChildren) {
    return (
      <span className="text-[11px] font-mono text-[#e8c07a]">{content}</span>
    )
  }

  // Unwrap polymorphic children
  const children = rawChildren?.map(child => unwrapXmpNode(child))
  const hasChildren = children && children.length > 0
  const attrCount = Array.isArray(attrs) ? attrs.length : 0

  return (
    <div className={`${depth > 0 ? 'ml-3 pl-3 border-l border-[var(--surface-3)]/30' : ''}`}>
      <div className="flex items-center gap-1.5 py-0.5">
        {hasChildren && (
          <button
            onClick={() => setOpen(o => !o)}
            className="text-[10px] text-[#555568] hover:text-[#888898] w-3 shrink-0"
          >
            {open ? '\u25BE' : '\u25B8'}
          </button>
        )}
        {!hasChildren && <span className="w-3 shrink-0" />}
        {name && (
          <span className="text-[11px] font-mono text-[#fb923c]">&lt;{name}&gt;</span>
        )}
        {ns && (
          <span className="text-[8px] font-mono px-1 py-px rounded bg-[var(--surface-2)] text-[#555568] border border-[var(--surface-3)]">
            {ns.split('/').pop()}
          </span>
        )}
        {attrCount > 0 && (
          <span className="text-[9px] font-mono text-[#666680]">
            [{attrCount} attrs]
          </span>
        )}
      </div>
      {open && hasChildren && (
        <div>
          {children!.map((child, i) => {
            // Detect text nodes (unwrapped XmpNode.Text or node with just content)
            const childContent = (child as Record<string, unknown>).content as string | undefined
            if (childContent !== undefined && !child.children && !child.name) {
              return (
                <div key={i} className="ml-6 py-0.5">
                  <span className="text-[11px] font-mono text-[#e8c07a]">{childContent}</span>
                </div>
              )
            }
            return <XmpNodeRenderer key={i} node={child} depth={depth + 1} />
          })}
        </div>
      )}
    </div>
  )
}

/* -- ICC ---------------------------------------------------------------- */

function renderIcc(value: Record<string, unknown>): React.ReactNode {
  const header = value.header as Record<string, unknown> | undefined
  const tags = value.tags as Array<Record<string, unknown>> | undefined

  const fields: Array<{ label: string; value: string }> = []
  if (header) {
    // Render all header fields dynamically - don't assume specific field names
    for (const [k, v] of Object.entries(header)) {
      if (v !== undefined && v !== null) {
        fields.push({ label: humanize(k), value: String(v) })
      }
    }
  }

  return (
    <div className="space-y-3">
      {fields.length > 0 && (
        <MetadataTable rows={fields} compact />
      )}
      {tags && tags.length > 0 && (
        <CollapsibleSection title={`Tags (${tags.length})`} defaultOpen={false}>
          <MetadataTable
            rows={tags.map(t => ({
              label: (t.signature as string) ?? '?',
              value: truncate(formatIccTagValue(t), 100),
            }))}
            compact
          />
        </CollapsibleSection>
      )}
      {!header && !tags && <FallbackRenderer value={value} />}
    </div>
  )
}

function formatIccTagValue(tag: Record<string, unknown>): string {
  if (tag.text) return String(tag.text)
  if (tag.value) return String(tag.value)
  if (tag.size) return `[${tag.size} bytes]`
  return '\u2014'
}

/* -- ID3v1 -------------------------------------------------------------- */

function renderId3v1(value: Record<string, unknown>): React.ReactNode {
  const fields = ['title', 'artist', 'album', 'year', 'comment', 'track', 'genre', 'genreName'] as const
  const rows = fields
    .filter(f => value[f] !== undefined && value[f] !== null && value[f] !== '')
    .map(f => ({
      label: humanize(f),
      value: String(value[f]),
    }))

  if (rows.length === 0) return <EmptyState text="No ID3v1 data" />

  const title = value.title as string | undefined
  const artist = value.artist as string | undefined
  const album = value.album as string | undefined

  return (
    <div className="space-y-3">
      {(title || artist) && (
        <div className="flex items-start gap-3">
          <div className="w-10 h-10 rounded-lg bg-[#38bdf8]/10 flex items-center justify-center text-lg shrink-0">
            {'\u{1F3B5}'}
          </div>
          <div className="min-w-0">
            {title && <div className="text-sm text-white font-medium truncate">{title}</div>}
            {artist && <div className="text-xs text-[#888898] truncate">{artist}</div>}
            {album && <div className="text-[11px] text-[#555568] truncate">{album}</div>}
          </div>
        </div>
      )}
      <MetadataTable rows={rows} compact />
    </div>
  )
}

/* -- ID3v2 -------------------------------------------------------------- */

function renderId3v2(value: Record<string, unknown>): React.ReactNode {
  const version = value.version as Record<string, unknown> | undefined
  const frames = value.frames as Array<Record<string, unknown>> | undefined
  const flags = value.flags as Record<string, unknown> | undefined

  const frameMap = new Map<string, string>()
  if (frames) {
    for (const f of frames) {
      const id = f.id as string
      const content = f.content as Record<string, unknown> | undefined
      if (!content) continue
      const text = (content.text as string) ?? (content.url as string) ?? (content.description as string)
      if (text && id) frameMap.set(id, text)
    }
  }

  const title = frameMap.get('TIT2')
  const artist = frameMap.get('TPE1') ?? frameMap.get('TPE2')
  const album = frameMap.get('TALB')
  const year = frameMap.get('TDRC') ?? frameMap.get('TYER')
  const genre = frameMap.get('TCON')

  return (
    <div className="space-y-3">
      {(title || artist) && (
        <div className="flex items-start gap-3 pb-2 border-b border-[var(--surface-3)]/40">
          <div className="w-12 h-12 rounded-lg bg-[#818cf8]/10 flex items-center justify-center text-xl shrink-0">
            {'\u{1F3B6}'}
          </div>
          <div className="min-w-0 flex-1">
            {title && <div className="text-sm text-white font-medium truncate">{title}</div>}
            {artist && <div className="text-xs text-[#888898] truncate">{artist}</div>}
            <div className="flex items-center gap-2 mt-0.5">
              {album && <span className="text-[11px] text-[#555568] truncate">{album}</span>}
              {year && <span className="text-[10px] text-[#444456]">({year})</span>}
              {genre && (
                <span className="text-[9px] font-mono px-1.5 py-0.5 rounded bg-[#818cf8]/10 text-[#818cf8]">
                  {genre}
                </span>
              )}
            </div>
          </div>
        </div>
      )}

      {version && (
        <div className="flex items-center gap-2 text-[10px] text-[#555568] font-mono">
          <span>ID3v2.{String(version.major ?? '?')}.{String(version.revision ?? 0)}</span>
          {flags && Object.entries(flags).filter(([, v]) => v === true).map(([k]) => (
            <span key={k} className="px-1.5 py-0.5 rounded bg-[var(--surface-2)] border border-[var(--surface-3)] text-[#666680]">
              {k}
            </span>
          ))}
        </div>
      )}

      {frames && frames.length > 0 && (
        <CollapsibleSection title={`Frames (${frames.length})`} defaultOpen={!title}>
          <MetadataTable
            rows={frames.map(f => ({
              label: (f.id as string) ?? '?',
              value: formatId3v2Frame(f),
            }))}
          />
        </CollapsibleSection>
      )}
    </div>
  )
}

function formatId3v2Frame(frame: Record<string, unknown>): string {
  const content = frame.content as Record<string, unknown> | undefined
  if (!content) return '\u2014'
  if ('text' in content) return truncate(String(content.text), 100)
  if ('url' in content) return truncate(String(content.url), 100)
  if ('description' in content && 'text' in content) {
    return `${content.description}: ${truncate(String(content.text), 80)}`
  }
  if ('mimeType' in content) {
    const size = (content.pictureData as unknown[])?.length
    return `[${content.mimeType}${size ? ` \u00B7 ${size} bytes` : ''}]`
  }
  return JSON.stringify(content).slice(0, 100)
}

/* -- PNG Text ----------------------------------------------------------- */

function renderPngText(value: Record<string, unknown>): React.ReactNode {
  const entries = value.entries as Array<Record<string, unknown>> | undefined
  if (!entries || entries.length === 0) return <EmptyState text="No text chunks" />

  return (
    <MetadataTable
      rows={entries.map(e => ({
        label: (e.keyword as string) ?? '?',
        value: truncate((e.text as string) ?? '', 120),
        badge: e.chunkType as string | undefined,
      }))}
    />
  )
}

/* -- Vorbis Comment ----------------------------------------------------- */

function renderVorbisComment(value: Record<string, unknown>): React.ReactNode {
  const vendor = value.vendor as string | undefined
  const comments = value.comments as Array<Record<string, unknown>> | undefined

  const commentMap = new Map<string, string>()
  if (comments) {
    for (const c of comments) {
      const field = (c.field as string)?.toUpperCase()
      const val = c.value as string
      if (field && val) commentMap.set(field, val)
    }
  }

  const title = commentMap.get('TITLE')
  const artist = commentMap.get('ARTIST')
  const album = commentMap.get('ALBUM')

  return (
    <div className="space-y-3">
      {(title || artist) && (
        <div className="flex items-start gap-3 pb-2 border-b border-[var(--surface-3)]/40">
          <div className="w-10 h-10 rounded-lg bg-[#a78bfa]/10 flex items-center justify-center text-lg shrink-0">
            {'\u{1F50A}'}
          </div>
          <div className="min-w-0">
            {title && <div className="text-sm text-white font-medium truncate">{title}</div>}
            {artist && <div className="text-xs text-[#888898] truncate">{artist}</div>}
            {album && <div className="text-[11px] text-[#555568] truncate">{album}</div>}
          </div>
        </div>
      )}

      {vendor && (
        <div className="text-[10px] font-mono text-[#555568]">
          Encoder: <span className="text-[#888898]">{vendor}</span>
        </div>
      )}

      {comments && comments.length > 0 && (
        <MetadataTable
          rows={comments.map(c => ({
            label: (c.field as string) ?? '?',
            value: truncate((c.value as string) ?? '', 100),
          }))}
          compact
        />
      )}
    </div>
  )
}

/* -- RIFF INFO ---------------------------------------------------------- */

function renderRiffInfo(value: Record<string, unknown>): React.ReactNode {
  const entries = value.entries as Array<Record<string, unknown>> | undefined
  if (!entries || entries.length === 0) return <EmptyState text="No INFO entries" />

  return (
    <MetadataTable
      rows={entries.map(e => ({
        label: (e.name as string) ?? (e.tag as string) ?? '?',
        value: (e.value as string) ?? '\u2014',
        badge: e.tag as string | undefined,
      }))}
    />
  )
}

/* -- iTunes -------------------------------------------------------------- */

const ITUNES_DATA_TYPE_NAMES: Record<number, string> = {
  0: 'implicit',
  1: 'UTF-8',
  2: 'UTF-16',
  13: 'JPEG',
  14: 'PNG',
  21: 'integer',
}

function renderItunes(value: Record<string, unknown>): React.ReactNode {
  const items = value.items as Array<Record<string, unknown>> | undefined
  if (!items || items.length === 0) return <EmptyState text="No iTunes metadata" />

  const itemMap = new Map<string, string>()
  for (const item of items) {
    const key = item.key as string
    const val = item.value as string
    if (key && val) itemMap.set(key, val)
  }

  const title = itemMap.get('\u00A9nam')
  const artist = itemMap.get('\u00A9ART') ?? itemMap.get('aART')
  const album = itemMap.get('\u00A9alb')

  return (
    <div className="space-y-3">
      {(title || artist) && (
        <div className="flex items-start gap-3 pb-2 border-b border-[var(--surface-3)]/40">
          <div className="w-10 h-10 rounded-lg bg-[#fb7185]/10 flex items-center justify-center text-lg shrink-0">
            {'\u{1F34E}'}
          </div>
          <div className="min-w-0">
            {title && <div className="text-sm text-white font-medium truncate">{title}</div>}
            {artist && <div className="text-xs text-[#888898] truncate">{artist}</div>}
            {album && <div className="text-[11px] text-[#555568] truncate">{album}</div>}
          </div>
        </div>
      )}

      <div className="space-y-0.5">
        {items.map((item, i) => {
          const dt = item.dataType as number | undefined
          const dtLabel = dt !== undefined && dt !== null
            ? (ITUNES_DATA_TYPE_NAMES[dt] ?? `type:${dt}`)
            : undefined
          return (
            <div
              key={i}
              className={`
                flex items-start gap-3 group py-0.5
                ${i > 0 ? 'border-t border-[var(--surface-3)]/20' : ''}
              `}
            >
              <div className="flex items-center gap-1.5 shrink-0 min-w-[120px] max-w-[180px]">
                <span className="text-[11px] font-mono text-[#888898] truncate"
                  title={(item.name as string) ?? (item.key as string) ?? '?'}>
                  {(item.name as string) ?? (item.key as string) ?? '?'}
                </span>
                {!!item.name && (
                  <span className="text-[8px] font-mono px-1 py-px rounded bg-[var(--surface-2)] text-[#555568] border border-[var(--surface-3)] shrink-0">
                    {String(item.key)}
                  </span>
                )}
              </div>
              <span className="text-[11px] font-mono text-[#c4c4d4] break-all min-w-0 flex-1">
                {truncate((item.value as string) ?? '\u2014', 100)}
              </span>
              {dtLabel && (
                <span className="text-[8px] font-mono px-1.5 py-px rounded bg-[#fb7185]/8 text-[#fb7185]/70 border border-[#fb7185]/15 shrink-0 whitespace-nowrap">
                  {dtLabel}
                </span>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}

/* -- Matroska Tags ------------------------------------------------------ */

function renderMatroskaTags(value: Record<string, unknown>): React.ReactNode {
  const tags = value.tags as Array<Record<string, unknown>> | undefined
  if (!tags || tags.length === 0) return <EmptyState text="No Matroska tags" />

  return (
    <div className="space-y-3">
      {tags.map((tag, i) => {
        const targetType = tag.targetType as string | undefined
        const targetTypeValue = tag.targetTypeValue as number | undefined
        const simpleTags = (tag.simpleTags as Array<Record<string, unknown>>) ?? []
        const trackUIDs = (tag.trackUIDs as number[]) ?? []
        const editionUIDs = (tag.editionUIDs as number[]) ?? []
        const chapterUIDs = (tag.chapterUIDs as number[]) ?? []
        const attachmentUIDs = (tag.attachmentUIDs as number[]) ?? []
        const hasUIDs = trackUIDs.length + editionUIDs.length + chapterUIDs.length + attachmentUIDs.length > 0

        const sectionTitle = targetType
          ? `${targetType}${targetTypeValue ? ` (${targetTypeValue})` : ''}`
          : targetTypeValue
            ? `Level ${targetTypeValue}`
            : `Tag Group ${i + 1}`

        return (
          <CollapsibleSection key={i} title={sectionTitle} defaultOpen={i === 0}>
            <div className="space-y-2">
              {hasUIDs && (
                <div className="flex flex-wrap gap-1.5 pb-1.5 border-b border-[var(--surface-3)]/20">
                  {trackUIDs.map((uid, j) => (
                    <span key={`t${j}`} className="text-[8px] font-mono px-1.5 py-0.5 rounded bg-[#fbbf24]/8 text-[#fbbf24]/80 border border-[#fbbf24]/15">
                      Track:{uid}
                    </span>
                  ))}
                  {editionUIDs.map((uid, j) => (
                    <span key={`e${j}`} className="text-[8px] font-mono px-1.5 py-0.5 rounded bg-[#38bdf8]/8 text-[#38bdf8]/80 border border-[#38bdf8]/15">
                      Edition:{uid}
                    </span>
                  ))}
                  {chapterUIDs.map((uid, j) => (
                    <span key={`c${j}`} className="text-[8px] font-mono px-1.5 py-0.5 rounded bg-[#4ade80]/8 text-[#4ade80]/80 border border-[#4ade80]/15">
                      Chapter:{uid}
                    </span>
                  ))}
                  {attachmentUIDs.map((uid, j) => (
                    <span key={`a${j}`} className="text-[8px] font-mono px-1.5 py-0.5 rounded bg-[#e879f9]/8 text-[#e879f9]/80 border border-[#e879f9]/15">
                      Attach:{uid}
                    </span>
                  ))}
                </div>
              )}
              <MatroskaSimpleTagList tags={simpleTags} depth={0} />
            </div>
          </CollapsibleSection>
        )
      })}
    </div>
  )
}

/** Recursively renders MatroskaSimpleTags, supporting nested children. */
function MatroskaSimpleTagList({ tags, depth }: { tags: Array<Record<string, unknown>>; depth: number }) {
  return (
    <div className={`${depth > 0 ? 'ml-3 pl-3 border-l border-[var(--surface-3)]/30' : ''} space-y-0.5`}>
      {tags.map((st, i) => {
        const children = (st.children as Array<Record<string, unknown>>) ?? []
        const isDefault = st.default as boolean | undefined
        const hasChildren = children.length > 0
        return (
          <div key={i}>
            <div
              className={`
                flex items-start gap-3 group py-0.5
                ${i > 0 ? 'border-t border-[var(--surface-3)]/20' : ''}
              `}
            >
              <div className="flex items-center gap-1.5 shrink-0 min-w-[120px] max-w-[180px]">
                <span className="text-[11px] font-mono text-[#888898] truncate" title={String(st.name)}>
                  {String(st.name ?? '?')}
                </span>
                {!!st.language && (
                  <span className="text-[8px] font-mono px-1 py-px rounded bg-[var(--surface-2)] text-[#555568] border border-[var(--surface-3)] shrink-0">
                    {String(st.language)}
                  </span>
                )}
                {isDefault === false && (
                  <span className="text-[8px] font-mono px-1 py-px rounded bg-[#fb923c]/10 text-[#fb923c]/70 border border-[#fb923c]/15 shrink-0">
                    alt
                  </span>
                )}
              </div>
              <span className="text-[11px] font-mono text-[#c4c4d4] break-all min-w-0">
                {(st.value as string)
                  ?? (st.binarySize ? `[binary: ${st.binarySize} bytes]` : hasChildren ? '' : '\u2014')}
              </span>
            </div>
            {hasChildren && (
              <MatroskaSimpleTagList tags={children} depth={depth + 1} />
            )}
          </div>
        )
      })}
    </div>
  )
}

// ===============================================================================
//  Shared UI components
// ===============================================================================

function MetadataTable({
  rows,
  compact,
}: {
  rows: Array<{ label: string; value: string; badge?: string }>
  compact?: boolean
}) {
  return (
    <div className={`${compact ? 'space-y-0.5' : 'space-y-1'}`}>
      {rows.map((row, i) => (
        <div
          key={`${row.label}-${i}`}
          className={`
            flex items-start gap-3 group
            ${compact ? 'py-0.5' : 'py-1'}
            ${i > 0 ? 'border-t border-[var(--surface-3)]/20' : ''}
          `}
        >
          <div className="flex items-center gap-1.5 shrink-0 min-w-[120px] max-w-[180px]">
            <span className="text-[11px] font-mono text-[#888898] truncate" title={row.label}>
              {row.label}
            </span>
            {row.badge && (
              <span className="text-[8px] font-mono px-1 py-px rounded bg-[var(--surface-2)] text-[#555568] border border-[var(--surface-3)] shrink-0">
                {row.badge}
              </span>
            )}
          </div>
          <span className="text-[11px] font-mono text-[#c4c4d4] break-all min-w-0">
            {row.value}
          </span>
        </div>
      ))}
    </div>
  )
}

function CollapsibleSection({
  title,
  defaultOpen = false,
  children,
}: {
  title: string
  defaultOpen?: boolean
  children: React.ReactNode
}) {
  const [open, setOpen] = useState(defaultOpen)
  return (
    <div className="rounded-lg border border-[var(--surface-3)]/40 overflow-hidden">
      <button
        onClick={() => setOpen(o => !o)}
        className="w-full flex items-center gap-2 px-3 py-2 text-left bg-[var(--surface-2)]/50 hover:bg-[var(--surface-2)] transition-colors"
      >
        <span className="text-[10px] font-mono text-[#555568]">
          {open ? '\u25BE' : '\u25B8'}
        </span>
        <span className="text-[11px] font-mono text-[#888898] uppercase tracking-wider">
          {title}
        </span>
      </button>
      <AnimatePresence initial={false}>
        {open && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.15 }}
            className="overflow-hidden"
          >
            <div className="px-3 py-2 max-h-80 overflow-y-auto">
              {children}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

function EmptyState({ text }: { text: string }) {
  return (
    <div className="text-[11px] font-mono text-[#444456] italic py-2">
      {text}
    </div>
  )
}

function FallbackRenderer({ value }: { value: Record<string, unknown> }) {
  return (
    <div className="code-block max-h-72 overflow-y-auto text-xs p-3 rounded-xl bg-[var(--surface-2)] border border-[var(--surface-3)]">
      <JsonTree value={value} depth={0} />
    </div>
  )
}

// ===============================================================================
//  JSON tree (retained for structure view + fallback)
// ===============================================================================

function JsonTree({ value, depth }: { value: unknown; depth: number }) {
  if (value === null || value === undefined) {
    return <span className="text-[#666680]">null</span>
  }
  if (typeof value === 'boolean') {
    return <span className="text-[var(--accent)]">{value ? 'true' : 'false'}</span>
  }
  if (typeof value === 'number') {
    return <span className="text-[#a8d8a8]">{value}</span>
  }
  if (typeof value === 'string') {
    return <span className="text-[#e8c07a]">&quot;{value}&quot;</span>
  }
  if (Array.isArray(value)) {
    return <JsonArray arr={value} depth={depth} />
  }
  if (typeof value === 'object') {
    return <JsonObject obj={value as Record<string, unknown>} depth={depth} />
  }
  return <span className="text-[#888898]">{String(value)}</span>
}

function JsonObject({ obj, depth }: { obj: Record<string, unknown>; depth: number }) {
  const [expanded, setExpanded] = useState(depth < 2)
  const entries = Object.entries(obj)

  if (entries.length === 0) {
    return <span className="text-[#555568]">{'{}'}</span>
  }

  return (
    <span>
      <button
        onClick={() => setExpanded(e => !e)}
        className="text-[#555568] hover:text-[#888898] transition-colors font-mono"
      >
        {expanded ? '\u25BE' : '\u25B8'}
      </button>
      {!expanded && (
        <span className="text-[#555568] ml-1 italic text-[10px]">
          {'{' + entries.length + ' fields}'}
        </span>
      )}
      {expanded && (
        <div className="pl-4 border-l border-[var(--surface-3)]/40 ml-1">
          {entries.map(([k, v]) => (
            <div key={k} className="my-0.5">
              <span className="text-[#888898]">{k}</span>
              <span className="text-[#555568]">: </span>
              <JsonTree value={v} depth={depth + 1} />
            </div>
          ))}
        </div>
      )}
    </span>
  )
}

function JsonArray({ arr, depth }: { arr: unknown[]; depth: number }) {
  const [expanded, setExpanded] = useState(depth < 1 && arr.length <= 8)

  if (arr.length === 0) {
    return <span className="text-[#555568]">[]</span>
  }

  return (
    <span>
      <button
        onClick={() => setExpanded(e => !e)}
        className="text-[#555568] hover:text-[#888898] transition-colors font-mono"
      >
        {expanded ? '\u25BE' : '\u25B8'}
      </button>
      <span className="text-[#555568] ml-1 text-[10px]">[{arr.length}]</span>
      {expanded && (
        <div className="pl-4 border-l border-[var(--surface-3)]/40 ml-1">
          {arr.map((item, i) => (
            <div key={i} className="my-0.5">
              <span className="text-[#555568] text-[10px] mr-1">{i}</span>
              <JsonTree value={item} depth={depth + 1} />
            </div>
          ))}
        </div>
      )}
    </span>
  )
}

// ===============================================================================
//  Utilities
// ===============================================================================

function truncate(s: string, max: number): string {
  return s.length > max ? s.slice(0, max) + '\u2026' : s
}

function humanize(camelCase: string): string {
  return camelCase
    .replace(/([A-Z])/g, ' $1')
    .replace(/^./, s => s.toUpperCase())
    .trim()
}
