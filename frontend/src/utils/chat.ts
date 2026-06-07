import type { AgentChunk, ChatTurn, SearchResult } from '../types'

export function createId(): string {
  return crypto.randomUUID()
}

export function createConversationId(): string {
  return crypto.randomUUID()
}

export function parseAgentChunk(raw: string): AgentChunk | null {
  try {
    const parsed = JSON.parse(raw) as AgentChunk
    if (parsed?.type) {
      return parsed
    }
  } catch {
    // not json
  }
  return null
}

export function applyChunk(turn: ChatTurn, chunk: AgentChunk): ChatTurn {
  switch (chunk.type) {
    case 'text':
      return {
        ...turn,
        content: turn.content + String(chunk.content ?? ''),
      }
    case 'thinking':
      return {
        ...turn,
        thinking: (turn.thinking ?? '') + String(chunk.content ?? ''),
      }
    case 'reference': {
      const refs = normalizeReferences(chunk.content)
      return { ...turn, references: refs }
    }
    case 'recommend': {
      const recs = normalizeRecommendations(chunk.content)
      return { ...turn, recommendations: recs }
    }
    case 'error':
      return {
        ...turn,
        content: turn.content || String(chunk.content ?? ''),
        status: 'error',
      }
    case 'complete':
      return { ...turn, status: 'complete' }
    default:
      return turn
  }
}

function normalizeReferences(content: unknown): SearchResult[] {
  if (Array.isArray(content)) {
    return content as SearchResult[]
  }
  if (typeof content === 'string') {
    try {
      return JSON.parse(content) as SearchResult[]
    } catch {
      return []
    }
  }
  return []
}

function normalizeRecommendations(content: unknown): string[] {
  if (Array.isArray(content)) {
    return content.map(String)
  }
  if (typeof content === 'string') {
    try {
      return JSON.parse(content) as string[]
    } catch {
      return []
    }
  }
  return []
}

export function sessionMessagesToTurns(
  messages: Array<{
    id: number
    question: string
    answer?: string
    thinking?: string
    reference?: SearchResult[]
    recommend?: string[]
  }>,
): ChatTurn[] {
  const turns: ChatTurn[] = []
  for (const msg of messages) {
    turns.push({
      id: `user-${msg.id}`,
      role: 'user',
      content: msg.question,
      dbId: msg.id,
    })
    if (msg.answer || msg.thinking) {
      turns.push({
        id: `assistant-${msg.id}`,
        role: 'assistant',
        content: msg.answer ?? '',
        thinking: msg.thinking,
        references: msg.reference,
        recommendations: msg.recommend,
        status: 'complete',
        dbId: msg.id,
      })
    }
  }
  return turns
}
