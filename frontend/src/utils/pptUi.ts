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

export function normalizeMinioFileUrl(fileUrl: string): string {
  try {
    const url = new URL(fileUrl)
    url.search = ''
    url.hash = ''
    return url.toString()
  } catch {
    const queryIndex = fileUrl.indexOf('?')
    const hashIndex = fileUrl.indexOf('#')
    let end = fileUrl.length
    if (queryIndex >= 0) end = Math.min(end, queryIndex)
    if (hashIndex >= 0) end = Math.min(end, hashIndex)
    return fileUrl.slice(0, end)
  }
}

export function isPptFileUrl(url: string): boolean {
  try {
    const parsed = new URL(url)
    return /\.pptx$/i.test(parsed.pathname) || parsed.pathname.includes('/ppt/')
  } catch {
    return /\.pptx(\?|$)/i.test(url) || url.includes('/ppt/')
  }
}

/** Replace bare MinIO PPT URLs with a markdown download link. */
export function formatPptDownloadLinks(content: string, linkLabel: string): string {
  if (!content || !linkLabel) return content

  const urlPattern = /https?:\/\/[^\s<>"')\]]+/g
  let formatted = content.replace(
    /\[([^\]]*)\]\((https?:\/\/[^\s)]+)\)/g,
    (match, _text, url) => (isPptFileUrl(url) ? `[${linkLabel}](${url})` : match),
  )

  let replaced = false
  formatted = formatted.replace(urlPattern, (match) => {
    if (!isPptFileUrl(match)) return match
    replaced = true
    return `[${linkLabel}](${match})`
  })

  if (replaced) return formatted

  const fileUrl = extractFileUrl(content)
  if (fileUrl) {
    return `${content.trim()}\n\n[${linkLabel}](${fileUrl})`
  }

  return formatted
}

export function extractFileUrl(text: string): string | null {
  const mdLink = text.match(/\]\((https?:\/\/[^\s)]+)\)/)
  const raw = mdLink?.[1] ?? text.match(/https?:\/\/[^\s<>"')\]]+/g)?.[0] ?? null
  return raw ? normalizeMinioFileUrl(raw) : null
}

export function extractSlideCount(text: string): number | null {
  const match = text.match(/(\d+)\s*pages?/i) || text.match(/totaling\s*(\d+)/i)
  return match ? Number(match[1]) : null
}

export function stripMarkdownInline(text: string): string {
  return text
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/\*([^*]+)\*/g, '$1')
    .replace(/__([^_]+)__/g, '$1')
    .replace(/_([^_]+)_/g, '$1')
    .replace(/`([^`]+)`/g, '$1')
    .trim()
}

export function extractPptTitle(content: string, fallback: string): string {
  const boldMatch = content.match(/\*\*([^*]+)\*\*/)
  if (boldMatch?.[1]) {
    return stripMarkdownInline(boldMatch[1].trim())
  }

  const aboutQuoted = content.match(/PPT about [「"']([^」"'\n]+)[」"']/i)
  if (aboutQuoted?.[1]) {
    return stripMarkdownInline(aboutQuoted[1].trim())
  }

  const about = content.match(/PPT about (.+?)(?:\s+has been|\s*,|\.\s|$)/i)
  if (about?.[1]) {
    return stripMarkdownInline(about[1].trim())
  }

  const created = content.match(/^(.+?)\s+has been created for you/i)
  if (created?.[1]) {
    return stripMarkdownInline(created[1].trim())
  }

  const file = content.match(/([\w\u4e00-\u9fff\s-]+)\.pptx/i)
  if (file) return `${stripMarkdownInline(file[1].trim())}.pptx`

  return stripMarkdownInline(fallback)
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
