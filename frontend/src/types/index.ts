export interface SearchResult {
  url: string
  title: string
  content?: string
}

export type MessageStatus = 'streaming' | 'complete' | 'error' | 'stopped'

export interface ChatTurn {
  id: string
  role: 'user' | 'assistant'
  content: string
  thinking?: string
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

export interface AgentChunk {
  type: AgentChunkType
  content?: string | SearchResult[] | string[]
  count?: number
}

export const WEB_SEARCH_AGENT_TYPE = 'webSearchReactAgent'
