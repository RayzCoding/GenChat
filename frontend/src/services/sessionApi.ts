import type { PageResult, SessionDetail, SessionSummary } from '../types'
import { WEB_SEARCH_AGENT_TYPE } from '../types'

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

async function getJson<T>(path: string, params?: Record<string, string | number | undefined>): Promise<T> {
  const response = await fetch(buildUrl(path, params))
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }
  return response.json() as Promise<T>
}

export function listSessions(page = 1, pageSize = 30): Promise<PageResult<SessionSummary>> {
  return getJson('/agent/sessions', {
    agentType: WEB_SEARCH_AGENT_TYPE,
    page,
    pageSize,
  })
}

export function searchSessions(
  q: string,
  page = 1,
  pageSize = 30,
): Promise<PageResult<SessionSummary>> {
  return getJson('/agent/sessions/search', {
    q,
    agentType: WEB_SEARCH_AGENT_TYPE,
    page,
    pageSize,
  })
}

export function getSessionDetail(conversationId: string): Promise<SessionDetail> {
  return getJson(`/agent/sessions/${encodeURIComponent(conversationId)}`)
}
