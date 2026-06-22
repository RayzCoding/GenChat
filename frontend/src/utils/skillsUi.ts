export interface SkillsThinkingStep {
  kind: 'tool' | 'text'
  label: string
  success?: boolean
  failed?: boolean
}

const TOOL_KEYWORDS = [
  'skill',
  'bash',
  'write_file',
  'edit_file',
  'read_skill',
  'grep',
  'tavily',
  'loadcontent',
  'websearch',
  'read',
  'write',
  'execute',
]

export function parseSkillsThinkingSteps(thinking: string): SkillsThinkingStep[] {
  if (!thinking.trim()) return []

  return thinking
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const failed = /❌|failed|error/i.test(line)
      const success = /✅|completed|done|success/i.test(line)
      const lower = line.toLowerCase()
      const isTool =
        TOOL_KEYWORDS.some((kw) => lower.includes(kw)) ||
        /tool|invoke|calling|execut/i.test(line)

      if (isTool) {
        return {
          kind: 'tool' as const,
          label: line.replace(/^✅\s*|^❌\s*/, ''),
          success: success || (!failed && line.length > 0),
          failed,
        }
      }

      return { kind: 'text' as const, label: line, success, failed }
    })
}

export function extractFileLinks(content: string): string[] {
  const links: string[] = []
  const mdLinks = content.matchAll(/\]\((https?:\/\/[^\s)]+)\)/g)
  for (const m of mdLinks) links.push(m[1])
  const plain = content.match(/https?:\/\/[^\s<>"')\]]+/g)
  if (plain) links.push(...plain)
  const fileNames = content.match(/[\w\u4e00-\u9fff\s-]+\.(pptx|pdf|docx|xlsx|md|txt)/gi)
  if (fileNames) {
    for (const name of fileNames) {
      if (!links.some((l) => l.includes(name))) {
        links.push(name.trim())
      }
    }
  }
  return [...new Set(links)]
}
