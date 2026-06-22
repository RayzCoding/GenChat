import type { ToolCallStep } from '../types'

export function isToolResultFailed(result?: string): boolean {
  if (!result) return false
  return /"error"|not found|failed/i.test(result)
}

export function formatToolLabel(step: ToolCallStep): string {
  const name = step.toolName.toLowerCase()

  if (name.includes('loadcontent')) {
    return '📖 Retrieving file contents...'
  }

  if (name.includes('search') || name.includes('tavily') || name.includes('websearch')) {
    const query = extractQueryArg(step.arguments)
    return query ? `🔍 Searching: ${query}` : '🔍 Searching for related information'
  }

  if (name.includes('read') || name.includes('grep')) {
    return `📄 ${step.toolName}`
  }

  if (name.includes('write') || name.includes('edit') || name.includes('bash')) {
    return `⚙️ ${step.toolName}`
  }

  return step.toolName
}

function extractQueryArg(argumentsJson?: string): string | null {
  if (!argumentsJson) return null
  try {
    const parsed = JSON.parse(argumentsJson) as { query?: string }
    return parsed.query?.trim() || null
  } catch {
    return null
  }
}

export function mergeToolSteps(steps: ToolCallStep[]): ToolCallStep[] {
  return steps
}
