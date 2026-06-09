import type { DeepResearchPhase } from '../types/deepResearch'

/**
 * 后端 thinking 日志关键字 → 前端阶段映射（来自 DeepResearchAgent.java）
 * 用于 reduceDeepResearchState 状态转移
 */
export const DEEP_RESEARCH_MARKERS = {
  clarifyStart: '🔍Your needs are being analyzed',
  clarifyDone: '✅Requirements analysis completed',
  clarifySufficient: 'Sufficient information and ready to generate a research topic',
  clarifyNeedMore: '【Additional information is needed】',
  pausePrefix: '⏸【Pause for in-depth research】',

  topicStart: '📝Research topics are being generated',
  topicDone: '✅ The research topic has been generated',

  roundStart: /🔄The(\d+)first round of research began/,

  planStart: '📋 An execution plan is being generated',
  planDone: /✅ 执行计划已生成，共 (\d+) 个任务/,
  planTableHeader: '📋 执行计划表：',
  planTaskLine: /🟠\s*(.+)/,

  executeStart: '--- 开始执行任务 ---',
  executeDone: '--- 任务执行完成 ---',
  taskExecuting: /⚙️ 正在执行任务 (\S+) : (.+)/,
  taskResult: '执行结果:',
  taskFailed: /❌ 任务 (\S+) 执行失败/,

  critiqueStart: '🔍 正在评估当前研究结果',
  critiquePassed: '✅ 研究结果评估通过',
  critiqueFailed: '⚠️ 研究结果评估未通过',
  nextRound: '--- 准备进入下一轮迭代 ---',

  compressStart: '📦 上下文过长，正在压缩',
  compressDone: '✅ 上下文压缩完成',

  researchDone: '✅ 研究阶段完成，准备生成最终报告',
  summarizeStart: '📝 正在生成最终研究报告',

  userStopped: '⏹ 用户已停止生成',
} as const

/** i18n key: deepResearch.phase.{phase} */
export function getDeepResearchPhaseKey(phase: DeepResearchPhase): string {
  return `deepResearch.phase.${phase}`
}

/** 是否在运行中（可显示 Stop） */
export function isDeepResearchRunning(phase: DeepResearchPhase): boolean {
  return ![
    'idle',
    'awaiting_clarification',
    'complete',
    'stopped',
    'error',
  ].includes(phase)
}
