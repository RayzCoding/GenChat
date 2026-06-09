import { useCallback, useRef, useState } from 'react'
import { stopAgent, streamSkills } from '../services/agentApi'
import type { ChatTurn } from '../types'
import { applyChunk, createId, parseAgentChunk } from '../utils/chat'

interface UseSkillsStreamOptions {
  conversationId: string
  onSessionUpdated?: () => void
}

export function useSkillsStream({ conversationId, onSessionUpdated }: UseSkillsStreamOptions) {
  const [turns, setTurns] = useState<ChatTurn[]>([])
  const [isStreaming, setIsStreaming] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const abortRef = useRef<AbortController | null>(null)

  const setTurnsExternal = useCallback((next: ChatTurn[]) => {
    setTurns(next)
    setError(null)
  }, [])

  const sendMessage = useCallback(
    async (question: string, t: (key: string) => string, fileId?: string) => {
      const trimmed = question.trim()
      if (!trimmed || isStreaming) return

      setError(null)
      const userTurn: ChatTurn = { id: createId(), role: 'user', content: trimmed }
      const assistantId = createId()
      const assistantTurn: ChatTurn = {
        id: assistantId,
        role: 'assistant',
        content: '',
        thinking: '',
        status: 'streaming',
      }

      setTurns((prev) => [...prev, userTurn, assistantTurn])
      setIsStreaming(true)

      const controller = new AbortController()
      abortRef.current = controller

      try {
        await streamSkills(conversationId, trimmed, fileId, controller.signal, (line) => {
          const chunk = parseAgentChunk(line)
          if (!chunk) return

          setTurns((prev) =>
            prev.map((turn) => (turn.id === assistantId ? applyChunk(turn, chunk) : turn)),
          )
        })

        setTurns((prev) =>
          prev.map((turn) =>
            turn.id === assistantId && turn.status === 'streaming'
              ? { ...turn, status: 'complete' }
              : turn,
          ),
        )
        onSessionUpdated?.()
      } catch (err) {
        if (controller.signal.aborted) {
          setTurns((prev) =>
            prev.map((turn) =>
              turn.id === assistantId ? { ...turn, status: 'stopped' } : turn,
            ),
          )
        } else {
          const message =
            err instanceof Error && err.message.includes('in progress')
              ? t('skills.error.inProgress')
              : t('skills.error.generic')
          setError(message)
          setTurns((prev) =>
            prev.map((turn) =>
              turn.id === assistantId
                ? { ...turn, content: message, status: 'error' }
                : turn,
            ),
          )
        }
      } finally {
        setIsStreaming(false)
        abortRef.current = null
      }
    },
    [conversationId, isStreaming, onSessionUpdated],
  )

  const stopGeneration = useCallback(async () => {
    abortRef.current?.abort()
    try {
      await stopAgent(conversationId)
    } catch {
      // ignore
    }
    setIsStreaming(false)
  }, [conversationId])

  return {
    turns,
    isStreaming,
    error,
    sendMessage,
    stopGeneration,
    setTurns: setTurnsExternal,
    clearError: () => setError(null),
  }
}
