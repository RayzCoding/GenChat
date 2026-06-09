import type { SearchResult } from './index'

/** 与后端 DeepResearchAgent 阶段一一对应 */
export type DeepResearchPhase =
  | 'idle'
  | 'clarifying'
  | 'awaiting_clarification'
  | 'generating_topic'
  | 'planning'
  | 'executing'
  | 'critiquing'
  | 'compressing'
  | 'summarizing'
  | 'complete'
  | 'stopped'
  | 'error'

export type PlanTaskStatus = 'pending' | 'executing' | 'completed' | 'failed'

export interface DeepResearchPlanTask {
  id: string
  instruction: string
  status: PlanTaskStatus
  round: number
}

export interface DeepResearchState {
  phase: DeepResearchPhase
  question: string
  researchTopic: string
  round: number
  maxRounds: number
  tasks: DeepResearchPlanTask[]
  thinkingLog: string
  report: string
  references: SearchResult[]
  /** 需求不足时后端 text 中的追问内容 */
  clarificationPrompt: string
  critiquePassed: boolean | null
  /** 当前正在执行的任务 id（并行时仅跟踪日志顺序） */
  activeTaskId: string | null
}

export const DEEP_RESEARCH_MAX_ROUNDS = 3

export const INITIAL_DEEP_RESEARCH_STATE: DeepResearchState = {
  phase: 'idle',
  question: '',
  researchTopic: '',
  round: 0,
  maxRounds: DEEP_RESEARCH_MAX_ROUNDS,
  tasks: [],
  thinkingLog: '',
  report: '',
  references: [],
  clarificationPrompt: '',
  critiquePassed: null,
  activeTaskId: null,
}
