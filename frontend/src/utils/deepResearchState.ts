import type { AgentChunk } from '../types'
import { RECOVERABLE_STREAM_ERROR_CODES } from '../types'
import type {
  DeepResearchPhase,
  DeepResearchPlanTask,
  DeepResearchState,
  ThinkingTimelineEntry,
} from '../types/deepResearch'
import { INITIAL_DEEP_RESEARCH_STATE } from '../types/deepResearch'
import { DEEP_RESEARCH_MARKERS } from './deepResearchMarkers'
import { isDisplayableThinkingLine, sanitizeThinkingLineText } from './thinkingText'

function setPhase(state: DeepResearchState, phase: DeepResearchPhase): DeepResearchState {
  if (state.phase === phase) return state
  return { ...state, phase }
}

function updateTask(
  tasks: DeepResearchPlanTask[],
  id: string,
  patch: Partial<DeepResearchPlanTask>,
): DeepResearchPlanTask[] {
  return tasks.map((t) => (t.id === id ? { ...t, ...patch } : t))
}

function parsePlanTasks(text: string, round: number): DeepResearchPlanTask[] {
  const tasks: DeepResearchPlanTask[] = []
  const headerIdx = text.lastIndexOf(DEEP_RESEARCH_MARKERS.planTableHeader)
  if (headerIdx < 0) return tasks

  const section = text.slice(headerIdx)
  for (const line of section.split('\n')) {
    const match = line.match(DEEP_RESEARCH_MARKERS.planTaskLine)
    if (!match) continue
    const instruction = match[1].trim()
    tasks.push({
      id: `r${round}-t${tasks.length + 1}`,
      instruction,
      status: 'pending',
      round,
    })
  }
  return tasks
}

function mergePlanTasks(
  existing: DeepResearchPlanTask[],
  newPlanTasks: DeepResearchPlanTask[],
  round: number,
): DeepResearchPlanTask[] {
  const kept = existing.filter((task) => {
    if (task.round !== round) return true
    if (task.status !== 'pending') return true
    return !newPlanTasks.some((planned) => planned.instruction === task.instruction)
  })
  const pendingInstructions = new Set(
    kept.filter((task) => task.round === round && task.status === 'pending').map((t) => t.instruction),
  )
  const additions = newPlanTasks.filter((task) => !pendingInstructions.has(task.instruction))
  return [...kept, ...additions]
}

function findPendingTaskByInstruction(
  tasks: DeepResearchPlanTask[],
  instruction: string,
  round: number,
): number {
  const normalized = instruction.trim()
  return tasks.findIndex(
    (task) =>
      task.instruction === normalized &&
      task.round === round &&
      (task.status === 'pending' || task.id.startsWith('r')),
  )
}

function extractDisplayLines(log: string): string[] {
  return log
    .split('\n')
    .map((line) => sanitizeThinkingLineText(line))
    .filter(isDisplayableThinkingLine)
}

function syncThinkingTimeline(
  entries: ThinkingTimelineEntry[],
  log: string,
  limit = 30,
): ThinkingTimelineEntry[] {
  const lines = extractDisplayLines(log).slice(-limit)
  return lines.map((text, index) => {
    const prev = entries[index]
    if (prev && sanitizeThinkingLineText(prev.text) === text) {
      return prev.text === text ? prev : { ...prev, text }
    }
    return {
      id: `${index}-${text.slice(0, 24)}`,
      text,
      timestamp: Date.now(),
    }
  })
}

function applyThinkingMarkers(state: DeepResearchState, log: string): DeepResearchState {
  let next = state

  if (log.includes(DEEP_RESEARCH_MARKERS.clarifyStart)) {
    next = setPhase(next, 'clarifying')
  }
  if (log.includes(DEEP_RESEARCH_MARKERS.topicStart)) {
    next = setPhase(next, 'generating_topic')
  }
  if (log.includes(DEEP_RESEARCH_MARKERS.planStart)) {
    next = setPhase(next, 'planning')
  }
  if (log.includes(DEEP_RESEARCH_MARKERS.executeStart)) {
    next = setPhase(next, 'executing')
  }
  if (log.includes(DEEP_RESEARCH_MARKERS.critiqueStart)) {
    next = setPhase(next, 'critiquing')
  }
  if (log.includes(DEEP_RESEARCH_MARKERS.compressStart)) {
    next = setPhase(next, 'compressing')
  }
  if (log.includes(DEEP_RESEARCH_MARKERS.researchDone) || log.includes(DEEP_RESEARCH_MARKERS.summarizeStart)) {
    next = setPhase(next, 'summarizing')
  }

  const roundMatch = [...log.matchAll(new RegExp(DEEP_RESEARCH_MARKERS.roundStart.source, 'g'))].pop()
  if (roundMatch) {
    const round = Number(roundMatch[1])
    next = { ...next, round }
  }

  if (log.includes(DEEP_RESEARCH_MARKERS.planTableHeader)) {
    const round = next.round || 1
    const newPlanTasks = parsePlanTasks(log, round)
    if (newPlanTasks.length > 0) {
      next = { ...next, tasks: mergePlanTasks(next.tasks, newPlanTasks, round) }
    }
  }

  const execMatches = [...log.matchAll(new RegExp(DEEP_RESEARCH_MARKERS.taskExecuting.source, 'g'))]
  const lastExec = execMatches.pop()
  if (lastExec) {
    const [, taskId, instruction] = lastExec
    const round = next.round || 1
    const normalized = instruction.trim()
    const exists = next.tasks.some((t) => t.id === taskId)
    let tasks = next.tasks
    if (!exists) {
      const pendingIdx = findPendingTaskByInstruction(tasks, normalized, round)
      if (pendingIdx >= 0) {
        tasks = tasks.map((task, index) =>
          index === pendingIdx
            ? { ...task, id: taskId, status: 'executing', instruction: normalized }
            : task,
        )
      } else {
        tasks = [...tasks, { id: taskId, instruction: normalized, status: 'executing', round }]
      }
    } else {
      tasks = tasks.map((t) =>
        t.id === taskId
          ? { ...t, status: 'executing', instruction: normalized }
          : t.status === 'executing' && t.id !== taskId
            ? { ...t, status: 'completed' }
            : t,
      )
    }
    next = { ...next, tasks, activeTaskId: taskId }
  }

  if (log.includes(DEEP_RESEARCH_MARKERS.taskResult)) {
    const execMatchesForResult = [...log.matchAll(new RegExp(DEEP_RESEARCH_MARKERS.taskExecuting.source, 'g'))]
    const resultCount = (log.match(/Execution result:/g) ?? []).length
    const lastExecuted = execMatchesForResult[Math.min(resultCount, execMatchesForResult.length) - 1]
    const completedTaskId = lastExecuted?.[1] ?? next.activeTaskId
    if (completedTaskId) {
      next = {
        ...next,
        tasks: updateTask(next.tasks, completedTaskId, { status: 'completed' }),
        activeTaskId: next.activeTaskId === completedTaskId ? null : next.activeTaskId,
      }
    }
  }

  const failMatches = [...log.matchAll(new RegExp(DEEP_RESEARCH_MARKERS.taskFailed.source, 'g'))]
  const lastFail = failMatches.pop()
  if (lastFail) {
    const taskId = lastFail[1]
    next = {
      ...next,
      tasks: updateTask(next.tasks, taskId, { status: 'failed' }),
      activeTaskId: next.activeTaskId === taskId ? null : next.activeTaskId,
    }
  }

  if (log.includes(DEEP_RESEARCH_MARKERS.critiquePassed)) {
    next = { ...next, critiquePassed: true }
  }
  if (log.includes(DEEP_RESEARCH_MARKERS.critiqueFailed)) {
    next = { ...next, critiquePassed: false }
  }

  if (log.includes(DEEP_RESEARCH_MARKERS.topicDone)) {
    const topicStart = log.lastIndexOf(DEEP_RESEARCH_MARKERS.topicStart)
    const topicEnd = log.lastIndexOf(DEEP_RESEARCH_MARKERS.topicDone)
    if (topicStart >= 0 && topicEnd > topicStart) {
      const topic = log
        .slice(topicStart + DEEP_RESEARCH_MARKERS.topicStart.length, topicEnd)
        .replace(/^\s*\n/, '')
        .trim()
      if (topic) {
        next = { ...next, researchTopic: topic.slice(0, 500) }
      }
    }
  }

  return next
}

export function startDeepResearch(question: string): DeepResearchState {
  return {
    ...INITIAL_DEEP_RESEARCH_STATE,
    question,
    phase: 'clarifying',
  }
}

export function reduceDeepResearchState(
  state: DeepResearchState,
  chunk: AgentChunk,
): DeepResearchState {
  switch (chunk.type) {
    case 'thinking': {
      const delta = String(chunk.content ?? '')
      const thinkingLog = state.thinkingLog + delta
      const thinkingTimeline = syncThinkingTimeline(state.thinkingTimeline, thinkingLog)
      let next = { ...state, thinkingLog, thinkingTimeline }
      next = applyThinkingMarkers(next, thinkingLog)
      return next
    }
    case 'text': {
      const text = String(chunk.content ?? '')
      if (
        text.includes(DEEP_RESEARCH_MARKERS.pausePrefix) ||
        text.includes(DEEP_RESEARCH_MARKERS.clarifyNeedMore)
      ) {
        const prompt = text
          .replace(DEEP_RESEARCH_MARKERS.pausePrefix, '')
          .replace(DEEP_RESEARCH_MARKERS.clarifyNeedMore, '')
          .trim()
        return {
          ...state,
          phase: 'awaiting_clarification',
          clarificationPrompt: prompt,
        }
      }
      if (text.includes(DEEP_RESEARCH_MARKERS.userStopped)) {
        return { ...state, phase: 'stopped', report: state.report + text }
      }
      const reportReady =
        state.phase === 'summarizing' ||
        state.report.length > 0 ||
        state.thinkingLog.includes(DEEP_RESEARCH_MARKERS.researchDone) ||
        state.thinkingLog.includes(DEEP_RESEARCH_MARKERS.summarizeStart)
      if (reportReady) {
        return {
          ...state,
          phase: 'summarizing',
          report: state.report + text,
        }
      }
      return state
    }
    case 'reference': {
      const refs = normalizeRefs(chunk.content)
      return {
        ...state,
        references: refs.length > 0 ? refs : state.references,
        phase: state.report ? 'complete' : state.phase,
      }
    }
    case 'error': {
      if (chunk.code && RECOVERABLE_STREAM_ERROR_CODES.has(chunk.code)) {
        const message = String(chunk.message ?? chunk.content ?? '')
        const warning = message ? `⚠️ ${message}\n` : ''
        return { ...state, thinkingLog: state.thinkingLog + warning }
      }
      return { ...state, phase: 'error' }
    }
    case 'complete':
      return state.phase === 'summarizing' || state.report
        ? { ...state, phase: 'complete' }
        : state
    default:
      return state
  }
}

function normalizeRefs(content: unknown) {
  if (Array.isArray(content)) return content as DeepResearchState['references']
  if (typeof content === 'string') {
    try {
      return JSON.parse(content) as DeepResearchState['references']
    } catch {
      return []
    }
  }
  return []
}

export function formatElapsed(ms: number): string {
  const totalSec = Math.floor(ms / 1000)
  const min = Math.floor(totalSec / 60)
  const sec = totalSec % 60
  if (min === 0) return `${sec}s`
  return `${min}m ${sec}s`
}
