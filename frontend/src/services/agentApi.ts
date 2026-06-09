const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

function buildUrl(path: string, params?: Record<string, string | number | undefined>): string {
  const url = new URL(`${API_BASE}${path}`, window.location.origin)
  if (params) {
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== '') {
        url.searchParams.set(key, String(value))
      }
    })
  }
  return url.toString()
}

export async function streamChat(
  conversationId: string,
  question: string,
  signal: AbortSignal,
  onChunk: (line: string) => void,
): Promise<void> {
  const url = buildUrl('/agent/chat/stream', { conversationId, question })
  const response = await fetch(url, { signal })

  if (!response.ok) {
    const text = await response.text().catch(() => '')
    throw new Error(text || `HTTP ${response.status}`)
  }

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
      const line = rawLine.trim()
      if (!line) continue

      if (line.startsWith('data:')) {
        onChunk(line.slice(5).trim())
      } else {
        onChunk(line)
      }
    }
  }

  if (buffer.trim()) {
    const line = buffer.trim()
    onChunk(line.startsWith('data:') ? line.slice(5).trim() : line)
  }
}

export async function stopAgent(conversationId: string): Promise<void> {
  const url = buildUrl('/agent/stop', { conversationId })
  await fetch(url)
}

export async function streamDeepResearch(
  conversationsId: string,
  question: string,
  signal: AbortSignal,
  onChunk: (line: string) => void,
): Promise<void> {
  const url = buildUrl('/agent/deep/stream', { conversationsId, question })
  const response = await fetch(url, { signal })

  if (!response.ok) {
    const text = await response.text().catch(() => '')
    throw new Error(text || `HTTP ${response.status}`)
  }

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
      const line = rawLine.trim()
      if (!line) continue
      onChunk(line.startsWith('data:') ? line.slice(5).trim() : line)
    }
  }

  if (buffer.trim()) {
    const line = buffer.trim()
    onChunk(line.startsWith('data:') ? line.slice(5).trim() : line)
  }
}

export async function streamPpt(
  conversationsId: string,
  question: string,
  signal: AbortSignal,
  onChunk: (line: string) => void,
): Promise<void> {
  const url = buildUrl('/agent/ppt/stream', { conversationsId, question })
  const response = await fetch(url, { signal })

  if (!response.ok) {
    const text = await response.text().catch(() => '')
    throw new Error(text || `HTTP ${response.status}`)
  }

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
      const line = rawLine.trim()
      if (!line) continue
      onChunk(line.startsWith('data:') ? line.slice(5).trim() : line)
    }
  }

  if (buffer.trim()) {
    const line = buffer.trim()
    onChunk(line.startsWith('data:') ? line.slice(5).trim() : line)
  }
}

export async function streamSkills(
  conversationsId: string,
  question: string,
  fileId: string | undefined,
  signal: AbortSignal,
  onChunk: (line: string) => void,
): Promise<void> {
  const url = buildUrl('/agent/skills/stream', { conversationsId, question, fileId })
  const response = await fetch(url, { signal })

  if (!response.ok) {
    const text = await response.text().catch(() => '')
    throw new Error(text || `HTTP ${response.status}`)
  }

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
      const line = rawLine.trim()
      if (!line) continue
      onChunk(line.startsWith('data:') ? line.slice(5).trim() : line)
    }
  }

  if (buffer.trim()) {
    const line = buffer.trim()
    onChunk(line.startsWith('data:') ? line.slice(5).trim() : line)
  }
}
