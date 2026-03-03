import type { TransformInfo } from './types'

export interface HistoryEntry {
  id: string
  tool: TransformInfo
  params: Record<string, string>
  inputHandle: string
  resultHandle: string
}
