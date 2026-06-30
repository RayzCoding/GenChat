import { readAgentSseStream } from '../utils/agentStream'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

function buildUrl(path: string, params?: Record<string, string | number | undefined>): string {
  const url = new URL(`${API_BASE}${path}`, window.location.origin)
  if (params) {
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== '') {
        url.searchParams.set(key, String(value))
      }
    })
  }
  return url.toString()
}

export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export interface FileInfoDto {
  id: number
  name: string
  path?: string
  fileType?: string
  size?: number
  extractedText?: string
  embed?: boolean
  status: 'pending' | 'processing' | 'success' | 'failed'
  createTime?: string
  updateTime?: string
}

export async function listFiles(): Promise<FileInfoDto[]> {
  const response = await fetch(`${API_BASE}/file`)
  const result = (await response.json()) as ApiResult<FileInfoDto[]>
  if (!response.ok || result.code !== 200 || !result.data) {
    throw new Error(result.message || `List failed (${response.status})`)
  }
  return result.data
}

export async function uploadFile(file: File): Promise<FileInfoDto> {
  const formData = new FormData()
  formData.append('file', file)

  const response = await fetch(`${API_BASE}/file/upload`, {
    method: 'POST',
    body: formData,
  })

  const result = (await response.json()) as ApiResult<FileInfoDto>
  if (!response.ok || result.code !== 200 || !result.data) {
    throw new Error(result.message || `Upload failed (${response.status})`)
  }
  return result.data
}

export async function deleteFile(id: number): Promise<void> {
  const response = await fetch(`${API_BASE}/file/${id}`, { method: 'DELETE' })
  if (!response.ok) {
    const text = await response.text().catch(() => '')
    throw new Error(text || `Delete failed (${response.status})`)
  }
}

export async function streamFileChat(
  conversationId: string,
  question: string,
  fileId: string,
  signal: AbortSignal,
  onChunk: (line: string) => void,
): Promise<void> {
  const url = buildUrl('/agent/file/stream', { conversationId, question, fileId })
  const response = await fetch(url, {
    signal,
    headers: { Accept: 'text/event-stream' },
  })

  if (!response.ok) {
    const text = await response.text().catch(() => '')
    throw new Error(text || `HTTP ${response.status}`)
  }

  await readAgentSseStream(response, onChunk)
}
