export type PptPhase =
  | 'idle'
  | 'requirement'
  | 'search'
  | 'template'
  | 'outline'
  | 'schema'
  | 'render'
  | 'complete'
  | 'failed'
  | 'awaiting_brief'

const PHASE_MARKERS: Array<{ phase: PptPhase; patterns: RegExp[] }> = [
  { phase: 'requirement', patterns: [/Your needs are being analyzed/i, /Start creating new PPT/i] },
  { phase: 'search', patterns: [/Information is being collected/i, /Once the relevant information is gathered/i] },
  { phase: 'template', patterns: [/Template styling is being designed/i, /Once the template is designed/i] },
  { phase: 'outline', patterns: [/After the outline is generated/i, /Outline generate/i] },
  {
    phase: 'schema',
    patterns: [
      /PPT details are being designed/i,
      /Image is being generated/i,
      /PPT content design is completed/i,
      /PPT is being modified/i,
    ],
  },
  { phase: 'render', patterns: [/Rendering PPT/i, /Rendering PPT Completed/i] },
  { phase: 'complete', patterns: [/PPT has been successfully generated/i, /successfully modified/i] },
]

export function detectPptPhase(thinking: string, content: string): PptPhase {
  if (content.includes('[PAUSE PPT GENERATION]') || thinking.includes('[PAUSE PPT GENERATION]')) {
    return 'awaiting_brief'
  }
  if (extractFileUrl(content)) {
    return 'complete'
  }

  let phase: PptPhase = 'idle'
  const combined = `${thinking}\n${content}`
  for (const item of PHASE_MARKERS) {
    if (item.patterns.some((p) => p.test(combined))) {
      phase = item.phase
    }
  }
  return phase
}

export function extractFileUrl(text: string): string | null {
  const mdLink = text.match(/\]\((https?:\/\/[^\s)]+)\)/)
  if (mdLink) return mdLink[1]
  const plain = text.match(/https?:\/\/[^\s<>"')\]]+/g)
  return plain?.[0] ?? null
}

export function extractSlideCount(text: string): number | null {
  const match = text.match(/(\d+)\s*pages?/i) || text.match(/共\s*(\d+)\s*页/) || text.match(/totaling\s*(\d+)/i)
  return match ? Number(match[1]) : null
}

export function extractPptTitle(content: string, fallback: string): string {
  const about = content.match(/PPT about [「"']?([^「"'\n.]+)/i)
  if (about) return about[1].trim()
  const file = content.match(/([\w\u4e00-\u9fff\s-]+)\.pptx/i)
  if (file) return `${file[1].trim()}.pptx`
  return fallback
}

export function getProgressSteps(phase: PptPhase): { outline: boolean; generating: boolean } {
  const order: PptPhase[] = ['outline', 'schema', 'render', 'complete']
  const idx = order.indexOf(phase)
  return {
    outline: idx >= 0 || phase === 'template' || phase === 'search' || phase === 'requirement',
    generating: phase === 'schema' || phase === 'render' || phase === 'complete',
  }
}

export function thinkingPreview(thinking: string, maxLen = 120): string {
  const line = thinking
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean)
    .pop()
  if (!line) return ''
  const cleaned = line.replace(/^✅\s*/, '').replace(/^🔍\s*/, '')
  return cleaned.length > maxLen ? `${cleaned.slice(0, maxLen)}…` : cleaned
}
