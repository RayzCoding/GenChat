/**
 * Normalize thinking-log lines for terminal-style display (plain text, no raw markdown).
 */
export function sanitizeThinkingLineText(text: string): string {
  let result = text.trim()
  if (!result) return ''

  result = result.replace(/[\s\S]*?<\/think>/gi, '')
  result = result.replace(/^#{1,6}\s+/gm, '')
  result = result.replace(/\*\*([^*]+)\*\*/g, '$1')
  result = result.replace(/__([^_]+)__/g, '$1')
  result = result.replace(/\*([^*]+)\*/g, '$1')
  result = result.replace(/_([^_]+)_/g, '$1')
  result = result.replace(/`([^`]+)`/g, '$1')
  result = result.replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
  result = result.replace(/^\s*[-*+]\s+/gm, '• ')
  result = result.replace(/\*\*/g, '').replace(/__/g, '').replace(/`/g, '')
  result = result.replace(/<[^>]+>/g, '')

  return result.replace(/\s{2,}/g, ' ').trim()
}

export function isDisplayableThinkingLine(text: string): boolean {
  if (!text || text.length <= 4) return false
  if (text.startsWith('---')) return false
  if (/^[\s*_`#•\-]+$/.test(text)) return false
  return true
}
