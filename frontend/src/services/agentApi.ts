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

async function streamAgentGet(
  path: string,
  params: Record<string, string | number | undefined>,
  signal: AbortSignal,
  onChunk: (line: string) => void,
): Promise<void> {
  const url = buildUrl(path, params)
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

export async function streamChat(
  conversationId: string,
  question: string,
  signal: AbortSignal,
  onChunk: (line: string) => void,
): Promise<void> {
  await streamAgentGet('/agent/chat/stream', { conversationId, question }, signal, onChunk)
}

export async function stopAgent(conversationId: string): Promise<void> {
  const url = buildUrl('/agent/stop', { conversationId })
  await fetch(url)
}

export async function streamDeepResearch(
  conversationsId: string,
  question: string,
  signal: AbortSignal,
  onChunk: (line: string) => void,
): Promise<void> {
  await streamAgentGet('/agent/deep/stream', { conversationsId, question }, signal, onChunk)
}

export async function streamPpt(
  conversationsId: string,
  question: string,
  signal: AbortSignal,
  onChunk: (line: string) => void,
): Promise<void> {
  await streamAgentGet('/agent/ppt/stream', { conversationsId, question }, signal, onChunk)
}

export async function streamSkills(
  conversationsId: string,
  question: string,
  fileId: string | undefined,
  signal: AbortSignal,
  onChunk: (line: string) => void,
): Promise<void> {
  await streamAgentGet('/agent/skills/stream', { conversationsId, question, fileId }, signal, onChunk)
}
