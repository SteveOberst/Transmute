/* -- Shared DTO types matching the Kotlin shared module --------------- */

export type MediaDomain = 'IMAGE' | 'AUDIO' | 'VIDEO'

export type ParameterType =
  | 'INT'
  | 'FLOAT'
  | 'BOOLEAN'
  | 'STRING'
  | 'ENUM'
  | 'INT_ARRAY'

/* /api/upload */
export interface FileHandle {
  handle: string
  originalName: string
  fileSize: number
  domain?: MediaDomain
  format?: string
}

/* /api/health */
export interface HealthResponse {
  status: string
  pluginCount: number
  imageFormats: number
  audioFormats: number
  videoFormats: number
  diagnostics: Record<string, boolean>
}

/* /api/formats */
export interface FormatInfo {
  name: string
  domain: MediaDomain
  canDecode: boolean
  canEncode: boolean
  hasStructureReader?: boolean
  providedBy?: string
  encodeOptions?: OptionSchema[]
}

/* /api/transforms */
export interface TransformInfo {
  id: string
  domain: MediaDomain
  description: string
  parameters?: ParameterSchema[]
}

export interface ParameterSchema {
  name: string
  type: ParameterType
  required?: boolean
  default?: string | null
  min?: string | null
  max?: string | null
  enumValues?: string[]
  description: string
}

/* /api/inspect/{handle} */
export interface InspectResult {
  domain: MediaDomain
  format: string
  fileSize: number
  structure?: MediaStructure
  metadata?: MediaMetadata[]
}

/** JSON envelope for decoded metadata (EXIF, XMP, ICC, ID3, etc.) */
export interface MediaMetadata {
  /** Type discriminator, e.g. "transmute.exif" */
  type: string
  /** Metadata-type-specific fields - arbitrary nested JSON */
  value: Record<string, unknown>
}

/** JSON envelope for a decoded media file structure */
export interface MediaStructure {
  /** Type discriminator, e.g. "transmute.png" */
  type: string
  /** Format-specific fields - arbitrary nested JSON */
  value: Record<string, unknown>
}

/** JSON envelope for a decoded metadata block */
export interface MediaMetadata {
  /** Type discriminator, e.g. "transmute.exif" */
  type: string
  /** Format-specific metadata fields */
  value: Record<string, unknown>
}

/* /api/transform */
export interface TransformRequest {
  fileHandle: string
  outputFormat: string
  pipeline: TransformStep[]
  encodeOptions?: Record<string, string>
  metadataPolicy?: string
}

export interface TransformStep {
  transformId: string
  parameters?: Record<string, string | null>
}

export interface TransformResult {
  resultHandle: string
  outputFormat: string
  fileSize: number
  properties: Record<string, string>
  generatedCode: string
  durationMs: number
}

/* /api/plugins */
export interface PluginDescriptor {
  key: string
  name: string
  description: string
  version?: string
  enabled: boolean
  status?: PluginStatusInfo
  domains: MediaDomain[]
  features: FeatureDescriptor[]
  options?: OptionSchema[]
  addedFormats: string[]
}

export interface PluginStatusInfo {
  available: boolean
  reason?: string
  details?: Record<string, string>
}

export interface FeatureDescriptor {
  id: string
  name: string
  description: string
  defaultEnabled: boolean
  currentlyEnabled: boolean
}

export interface OptionSchema {
  id: string
  name: string
  type: ParameterType
  default?: string
  enumValues?: string[]
  description: string
}

export interface PluginUpdate {
  enabled?: boolean
  features?: Record<string, boolean>
}

/* WebSocket /ws/progress */
export interface ProgressEvent {
  jobId: string
  stage: string
  progress: number
  message?: string
}

/* /api/waveform/{handle} */
export interface WaveformData {
  samples: number[]
  sampleRate: number
}
