import type { DeepResearchPhase } from '../types/deepResearch'

/**
 * Backend thinking log markers → frontend phase mapping (from DeepResearchAgent.java).
 * Used by reduceDeepResearchState for state transitions.
 */
export const DEEP_RESEARCH_MARKERS = {
  clarifyStart: '🔍Your needs are being analyzed',
  clarifyDone: '✅Requirements analysis completed',
  clarifySufficient: 'Sufficient information and ready to generate a research topic',
  clarifyNeedMore: '【Additional information is needed】',
  pausePrefix: '⏸【Pause for in-depth research】',

  topicStart: '📝Research topics are being generated',
  topicDone: '✅ The research topic has been generated',

  roundStart: /🔄 Round (\d+) of research began/,

  planStart: '📋 An execution plan is being generated',
  planDone: /✅ Execution plan generated with (\d+) task\(s\)/,
  planTableHeader: '📋 Execution plan:',
  planTaskLine: /🟠\s*(.+)/,

  executeStart: '--- Starting task execution ---',
  executeDone: '--- Task execution completed ---',
  taskExecuting: /⚙️ Executing task (\S+) : (.+)/,
  taskResult: 'Execution result:',
  taskFailed: /❌ Task (\S+) failed/,

  critiqueStart: '🔍 Evaluating current research results',
  critiquePassed: '✅ Research evaluation passed',
  critiqueFailed: '⚠️ Research evaluation did not pass',
  nextRound: '--- Preparing next iteration ---',

  compressStart: '📦 Context too large, compressing',
  compressDone: '✅ Context compression completed',

  researchDone: '✅ Research phase completed, preparing final report',
  summarizeStart: '📝 Generating final research report',

  userStopped: '⏹ Generation stopped by user',
} as const

/** i18n key: deepResearch.phase.{phase} */
export function getDeepResearchPhaseKey(phase: DeepResearchPhase): string {
  return `deepResearch.phase.${phase}`
}

/** Whether the agent is running (show Stop button). */
export function isDeepResearchRunning(phase: DeepResearchPhase): boolean {
  return ![
    'idle',
    'awaiting_clarification',
    'complete',
    'stopped',
    'error',
  ].includes(phase)
}
