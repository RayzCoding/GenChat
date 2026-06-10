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
      const refs = normalizeReferences(chunk.content)
      return { ...turn, references: refs }
    }
    case 'recommend': {
      const recs = normalizeRecommendations(chunk.content)
      return { ...turn, recommendations: recs }
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
