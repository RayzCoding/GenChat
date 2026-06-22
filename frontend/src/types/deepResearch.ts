import type { SearchResult } from './index'

/** Maps 1:1 to backend DeepResearchAgent phases */
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
  /** Follow-up content from backend text when requirements are insufficient */
  clarificationPrompt: string
  critiquePassed: boolean | null
  /** Currently executing task id (log order only when tasks run in parallel) */
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
