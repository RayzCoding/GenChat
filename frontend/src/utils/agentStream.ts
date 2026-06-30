/**
 * Normalize one SSE / stream line into agent event JSON payload strings.
 */
export function expandStreamPayloads(raw: string): string[] {
  const trimmed = raw.trim()
  if (!trimmed) return []

  if (trimmed.startsWith('event:') || trimmed.startsWith('id:') || trimmed.startsWith(':')) {
    return []
  }

  const payload = trimmed.startsWith('data:') ? trimmed.slice(5).trim() : trimmed
  if (!payload) return []

  try {
    const parsed = JSON.parse(payload) as unknown
    if (Array.isArray(parsed)) {
      return parsed.flatMap((item) => {
        if (typeof item === 'string') {
          return expandStreamPayloads(item)
        }
        if (item && typeof item === 'object') {
          return [JSON.stringify(item)]
        }
        return []
      })
    }
  } catch {
    // single JSON object or plain text below
  }

  return [payload]
}

export async function readAgentSseStream(
  response: Response,
  onChunk: (line: string) => void,
): Promise<void> {
  const reader = response.body?.getReader()
  if (!reader) {
    throw new Error('No response body')
  }

  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''

    for (const rawLine of lines) {
      for (const payload of expandStreamPayloads(rawLine)) {
        onChunk(payload)
      }
    }
  }

  if (buffer.trim()) {
    for (const payload of expandStreamPayloads(buffer)) {
      onChunk(payload)
    }
  }
}
