import type { DeepResearchPhase, DeepResearchState, ThinkingTimelineEntry } from '../types/deepResearch'

export type StreamStatus = 'active' | 'queued' | 'pending' | 'done'

export interface ParallelStreamItem {
  key: 'web' | 'semantic' | 'drafting'
  status: StreamStatus
  width: number
}

export interface ThinkingLogEntry {
  id: string
  cycle: number
  text: string
}

const PHASE_PROGRESS: Record<DeepResearchPhase, number> = {
  idle: 0,
  clarifying: 12,
  awaiting_clarification: 12,
  generating_topic: 22,
  planning: 35,
  executing: 50,
  critiquing: 72,
  compressing: 78,
  summarizing: 88,
  complete: 100,
  stopped: 0,
  error: 0,
}

export function computeResearchProgress(state: DeepResearchState): number {
  let progress = PHASE_PROGRESS[state.phase] ?? 0

  if (state.phase === 'executing' && state.tasks.length > 0) {
    const done = state.tasks.filter(
      (t) => t.status === 'completed' || t.status === 'failed',
    ).length
    progress += Math.round((done / state.tasks.length) * 18)
  }

  if (state.phase === 'summarizing' && state.report.length > 0) {
    progress = Math.min(98, 88 + Math.min(Math.floor(state.report.length / 400), 10))
  }

  return Math.min(100, progress)
}

export function getParallelStreams(phase: DeepResearchPhase): ParallelStreamItem[] {
  const web: ParallelStreamItem = { key: 'web', status: 'pending', width: 5 }
  const semantic: ParallelStreamItem = { key: 'semantic', status: 'pending', width: 5 }
  const drafting: ParallelStreamItem = { key: 'drafting', status: 'pending', width: 5 }

  switch (phase) {
    case 'executing':
      web.status = 'active'
      web.width = 88
      semantic.status = 'queued'
      semantic.width = 30
      break
    case 'critiquing':
    case 'compressing':
    case 'planning':
      web.status = 'done'
      web.width = 100
      semantic.status = 'active'
      semantic.width = 65
      drafting.status = 'queued'
      drafting.width = 20
      break
    case 'summarizing':
      web.status = 'done'
      web.width = 100
      semantic.status = 'done'
      semantic.width = 100
      drafting.status = 'active'
      drafting.width = 72
      break
    case 'complete':
      web.status = 'done'
      web.width = 100
      semantic.status = 'done'
      semantic.width = 100
      drafting.status = 'done'
      drafting.width = 100
      break
    case 'generating_topic':
    case 'clarifying':
      web.status = 'queued'
      web.width = 25
      semantic.status = 'pending'
      semantic.width = 8
      break
    default:
      break
  }

  return [web, semantic, drafting]
}

export function parseThinkingLogEntries(
  log: string,
  round: number,
  limit = 6,
): ThinkingLogEntry[] {
  if (!log.trim()) return []

  const lines = log
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean)
    .filter((l) => !l.startsWith('---') && l.length > 4)

  const entries: ThinkingLogEntry[] = []
  let cycle = Math.max(round, 1) * 10

  for (let i = lines.length - 1; i >= 0 && entries.length < limit; i--) {
    const text = lines[i]
      .replace(/^🔄The\d+first round of research began\s*/i, '')
      .replace(/^📋 An execution plan is being generated\.{3}\s*/i, '')
      .trim()
    if (text.length < 8) continue
    entries.unshift({
      id: `${i}-${text.slice(0, 24)}`,
      cycle: cycle + entries.length,
      text,
    })
  }

  return entries.slice(-limit)
}

export function exportReportMarkdown(state: DeepResearchState): void {
  const content = [
    `# ${state.researchTopic || state.question}`,
    '',
    state.report,
    '',
    state.references.length > 0 ? '## References' : '',
    ...state.references.map((r) => `- [${r.title || r.url}](${r.url})`),
  ]
    .filter(Boolean)
    .join('\n')

  const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `deep-research-${Date.now()}.md`
  a.click()
  URL.revokeObjectURL(url)
}

export interface ThinkingTerminalLine {
  id: string
  timestamp: string
  text: string
  tone: 'system' | 'success' | 'active' | 'default'
}

export function formatThinkingTimeline(entries: ThinkingTimelineEntry[]): ThinkingTerminalLine[] {
  return entries.map((entry, index) => {
    const timestamp = new Date(entry.timestamp).toLocaleTimeString([], { hour12: false })
    let tone: ThinkingTerminalLine['tone'] = 'default'
    if (/✅|completed|passed/i.test(entry.text)) tone = 'success'
    else if (/🔍|initialized|Analyzing|generating|Executing|⚙️|📋|🔄|📝/i.test(entry.text))
      tone = index === entries.length - 1 ? 'active' : 'system'
    else if (/System|initialized/i.test(entry.text)) tone = 'system'

    return {
      id: entry.id,
      timestamp,
      text: entry.text,
      tone,
    }
  })
}

export function terminalLineClass(tone: ThinkingTerminalLine['tone'], isLast: boolean): string {
  switch (tone) {
    case 'success':
      return 'text-tertiary'
    case 'active':
      return isLast ? 'animate-pulse text-primary' : 'text-primary opacity-60'
    case 'system':
      return 'text-primary opacity-60'
    default:
      return 'text-on-surface opacity-80'
  }
}
