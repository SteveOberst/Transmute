import type {
  FileHandle,
  FormatInfo,
  HealthResponse,
  InspectResult,
  MediaDomain,
  MediaStructure,
  PluginDescriptor,
  PluginUpdate,
  TransformInfo,
  TransformRequest,
  TransformResult,
} from './types'

/* -- Base URL ---------------------------------------------------------- */

const BASE =
  process.env.NEXT_PUBLIC_API_URL ??
  (typeof window !== 'undefined' ? window.location.origin : 'http://localhost:8080')

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  })
  if (!res.ok) {
    const body = await res.text()
    throw new Error(`API ${res.status}: ${body}`)
  }
  return res.json() as Promise<T>
}

/* -- Health ------------------------------------------------------------ */

export async function fetchHealth(): Promise<HealthResponse> {
  return request('/api/health')
}

/* -- Formats & Transforms ---------------------------------------------- */

export async function fetchFormats(domain?: MediaDomain): Promise<FormatInfo[]> {
  const q = domain ? `?domain=${domain}` : ''
  return request(`/api/formats${q}`)
}

export async function fetchTransforms(domain?: MediaDomain): Promise<TransformInfo[]> {
  const q = domain ? `?domain=${domain}` : ''
  return request(`/api/transforms${q}`)
}

/* -- Upload & Files ---------------------------------------------------- */

export async function uploadFile(file: File): Promise<FileHandle> {
  const form = new FormData()
  form.append('file', file)
  const res = await fetch(`${BASE}/api/upload`, { method: 'POST', body: form })
  if (!res.ok) throw new Error(`Upload failed: ${res.status}`)
  return res.json() as Promise<FileHandle>
}

export async function inspectFile(handle: string): Promise<InspectResult> {
  return request(`/api/inspect/${handle}`, { method: 'POST' })
}

export function fileUrl(handle: string): string {
  return `${BASE}/api/files/${handle}`
}

/* -- Transform --------------------------------------------------------- */

export async function executeTransform(req: TransformRequest): Promise<TransformResult> {
  return request('/api/transform', {
    method: 'POST',
    body: JSON.stringify(req),
  })
}

/* -- Plugins ----------------------------------------------------------- */

export async function fetchPlugins(): Promise<PluginDescriptor[]> {
  return request('/api/plugins')
}

export async function fetchPlugin(key: string): Promise<PluginDescriptor> {
  return request(`/api/plugins/${key}`)
}

export async function updatePlugin(
  key: string,
  update: PluginUpdate,
): Promise<PluginDescriptor> {
  return request(`/api/plugins/${key}`, {
    method: 'PUT',
    body: JSON.stringify(update),
  })
}

/* -- Progress WebSocket ------------------------------------------------ */

export function connectProgress(
  onMessage: (data: unknown) => void,
  onClose?: () => void,
): WebSocket {
  const wsBase = BASE.replace(/^http/, 'ws')
  const ws = new WebSocket(`${wsBase}/ws/progress`)
  ws.onmessage = (e) => onMessage(JSON.parse(e.data))
  ws.onclose = () => onClose?.()
  return ws
}
