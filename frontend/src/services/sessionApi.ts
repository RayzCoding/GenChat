import type { ApiResult, PageResult, SessionDetail, SessionSummary } from '../types'

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

async function getResult<T>(path: string, params?: Record<string, string | number | undefined>): Promise<T> {
  const response = await fetch(buildUrl(path, params))
  const result = (await response.json()) as ApiResult<T>
  if (!response.ok || result.code !== 200) {
    throw new Error(result.message || `HTTP ${response.status}`)
  }
  return result.data as T
}

export function listSessions(page = 1, pageSize = 30): Promise<PageResult<SessionSummary>> {
  return getResult('/agent/sessions', { page, pageSize })
}

export function searchSessions(
  q: string,
  page = 1,
  pageSize = 30,
): Promise<PageResult<SessionSummary>> {
  return getResult('/agent/sessions/search', { q, page, pageSize })
}

export function getSessionDetail(conversationId: string): Promise<SessionDetail> {
  return getResult(`/agent/sessions/${encodeURIComponent(conversationId)}`)
}

export async function deleteSession(conversationId: string): Promise<void> {
  const response = await fetch(
    buildUrl(`/agent/sessions/${encodeURIComponent(conversationId)}`),
    { method: 'DELETE' },
  )
  const result = (await response.json()) as ApiResult<null>
  if (!response.ok || result.code !== 200) {
    throw new Error(result.message || `Delete failed (${response.status})`)
  }
}
