import type { AgentChunk, ChatTurn, SearchResult, ToolCallStep } from '../types'
import { RECOVERABLE_STREAM_ERROR_CODES } from '../types'
import { isToolResultFailed } from './toolSteps'

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
      const refs = dedupeReferences(normalizeReferences(chunk.content))
      return { ...turn, references: refs.length > 0 ? refs : turn.references }
    }
    case 'recommend': {
      const recs = normalizeRecommendations(chunk.content)
      return { ...turn, recommendations: recs.length > 0 ? recs : turn.recommendations }
    }
    case 'error': {
      const message = String(chunk.message ?? chunk.content ?? '')
      if (chunk.code && RECOVERABLE_STREAM_ERROR_CODES.has(chunk.code)) {
        const warning = message ? `⚠️ ${message}\n` : ''
        return {
          ...turn,
          thinking: (turn.thinking ?? '') + warning,
        }
      }
      return {
        ...turn,
        content: turn.content || message,
        status: 'error',
      }
    }
    case 'tool_start': {
      const toolCallId = chunk.toolCallId ?? ''
      const existing = turn.toolCalls ?? []
      if (existing.some((step) => step.toolCallId === toolCallId)) {
        return turn
      }
      const step: ToolCallStep = {
        toolCallId,
        toolName: chunk.toolName ?? 'tool',
        arguments: chunk.arguments,
        status: 'running',
      }
      return { ...turn, toolCalls: [...existing, step] }
    }
    case 'tool_end': {
      const toolCallId = chunk.toolCallId ?? ''
      const failed = isToolResultFailed(chunk.result)
      const steps = [...(turn.toolCalls ?? [])]
      const index = steps.findIndex((step) => step.toolCallId === toolCallId)
      if (index >= 0) {
        steps[index] = {
          ...steps[index],
          result: chunk.result,
          status: failed ? 'failed' : 'done',
        }
      } else {
        steps.push({
          toolCallId,
          toolName: chunk.toolName ?? 'tool',
          result: chunk.result,
          status: failed ? 'failed' : 'done',
        })
      }
      return { ...turn, toolCalls: steps }
    }
    case 'complete':
      return { ...turn, status: 'complete' }
    default:
      return turn
  }
}

function normalizeReferences(content: unknown): SearchResult[] {
  if (Array.isArray(content)) {
    return content.filter(isSearchResultLike).map(normalizeSearchResult)
  }
  if (typeof content === 'string') {
    try {
      const parsed = JSON.parse(content) as unknown
      if (Array.isArray(parsed)) {
        return parsed.filter(isSearchResultLike).map(normalizeSearchResult)
      }
    } catch {
      return []
    }
  }
  return []
}

function isSearchResultLike(value: unknown): value is SearchResult {
  if (!value || typeof value !== 'object') return false
  const record = value as Record<string, unknown>
  return typeof record.url === 'string' || typeof record.title === 'string'
}

function normalizeSearchResult(value: SearchResult): SearchResult {
  return {
    url: value.url ?? '',
    title: value.title ?? '',
    content: value.content,
  }
}

function dedupeReferences(references: SearchResult[]): SearchResult[] {
  const seen = new Set<string>()
  const deduped: SearchResult[] = []
  for (const ref of references) {
    const key = ref.url || ref.title
    if (!key || seen.has(key)) continue
    seen.add(key)
    deduped.push(ref)
  }
  return deduped
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
    const references = msg.reference?.length ? dedupeReferences(msg.reference) : undefined
    const recommendations = msg.recommend?.length ? msg.recommend : undefined
    if (msg.answer || msg.thinking || references?.length || recommendations?.length) {
      turns.push({
        id: `assistant-${msg.id}`,
        role: 'assistant',
        content: msg.answer ?? '',
        thinking: msg.thinking,
        references,
        recommendations,
        status: 'complete',
        dbId: msg.id,
      })
    }
  }
  return turns
}

/** Preserve stream-time references/recommendations when history reload is incomplete. */
export function mergeSessionTurns(localTurns: ChatTurn[], loadedTurns: ChatTurn[]): ChatTurn[] {
  if (localTurns.length === 0) return loadedTurns

  const localAssistants = localTurns.filter((turn) => turn.role === 'assistant')
  const localByDbId = new Map(
    localAssistants
      .filter((turn) => turn.dbId != null)
      .map((turn) => [turn.dbId as number, turn]),
  )

  let assistantIndex = 0
  return loadedTurns.map((turn) => {
    if (turn.role !== 'assistant') return turn

    const localTurn =
      (turn.dbId != null ? localByDbId.get(turn.dbId) : undefined) ??
      localAssistants[assistantIndex]
    assistantIndex += 1

    if (!localTurn) return turn

    return {
      ...turn,
      references:
        turn.references?.length ? turn.references : localTurn.references,
      recommendations:
        turn.recommendations?.length ? turn.recommendations : localTurn.recommendations,
      toolCalls: turn.toolCalls?.length ? turn.toolCalls : localTurn.toolCalls,
    }
  })
}
