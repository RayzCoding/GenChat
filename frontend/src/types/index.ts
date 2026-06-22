export interface SearchResult {
  url: string
  title: string
  content?: string
}

export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export type MessageStatus = 'streaming' | 'complete' | 'error' | 'stopped'

export type ToolCallStatus = 'running' | 'done' | 'failed'

export interface ToolCallStep {
  toolCallId: string
  toolName: string
  arguments?: string
  result?: string
  status: ToolCallStatus
}

export interface ChatTurn {
  id: string
  role: 'user' | 'assistant'
  content: string
  thinking?: string
  toolCalls?: ToolCallStep[]
  references?: SearchResult[]
  recommendations?: string[]
  status?: MessageStatus
  dbId?: number
}

export interface SessionSummary {
  conversationId: string
  title: string
  lastQuestion?: string
  messageCount: number
  agentType: string
  updatedAt: string
}

export interface SessionMessage {
  id: number
  question: string
  answer?: string
  thinking?: string
  reference?: SearchResult[]
  recommend?: string[]
  createTime: string
  totalResponseTime?: number
}

export interface SessionDetail {
  conversationId: string
  messages: SessionMessage[]
}

export interface PageResult<T> {
  total: number
  items: T[]
}

export type AgentChunkType =
  | 'text'
  | 'thinking'
  | 'reference'
  | 'recommend'
  | 'error'
  | 'complete'
  | 'tool_start'
  | 'tool_end'

export interface AgentChunk {
  type: AgentChunkType
  content?: string | SearchResult[] | string[]
  count?: number
  code?: string
  message?: string
  detail?: string
  toolName?: string
  toolCallId?: string
  arguments?: string
  result?: string
}

/** Stream errors that indicate a retry attempt; do not terminate the turn. */
export const RECOVERABLE_STREAM_ERROR_CODES = new Set(['LLM_CALL_FAILED'])

export const WEB_SEARCH_AGENT_TYPE = 'webSearchReactAgent'
