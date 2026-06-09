import { useCallback, useEffect, useRef, useState } from 'react'
import { stopAgent, streamDeepResearch } from '../services/agentApi'
import type { DeepResearchState } from '../types/deepResearch'
import { INITIAL_DEEP_RESEARCH_STATE } from '../types/deepResearch'
import { parseAgentChunk } from '../utils/chat'
import { isDeepResearchRunning } from '../utils/deepResearchMarkers'
import { reduceDeepResearchState, startDeepResearch } from '../utils/deepResearchState'

export function useDeepResearchStream(conversationId: string) {
  const [state, setState] = useState<DeepResearchState>(INITIAL_DEEP_RESEARCH_STATE)
  const [error, setError] = useState<string | null>(null)
  const [elapsedMs, setElapsedMs] = useState(0)
  const abortRef = useRef<AbortController | null>(null)
  const timerRef = useRef<number | null>(null)
  const startedAtRef = useRef<number | null>(null)

  const clearTimer = useCallback(() => {
    if (timerRef.current != null) {
      window.clearInterval(timerRef.current)
      timerRef.current = null
    }
  }, [])

  const startTimer = useCallback(() => {
    clearTimer()
    startedAtRef.current = Date.now()
    setElapsedMs(0)
    timerRef.current = window.setInterval(() => {
      if (startedAtRef.current != null) {
        setElapsedMs(Date.now() - startedAtRef.current)
      }
    }, 1000)
  }, [clearTimer])

  useEffect(() => () => clearTimer(), [clearTimer])

  const isStreaming = isDeepResearchRunning(state.phase)

  const sendQuestion = useCallback(
    async (question: string, t: (key: string) => string) => {
      const trimmed = question.trim()
      if (!trimmed || isStreaming) return

      setError(null)

      const isResume = state.phase === 'awaiting_clarification'
      const apiQuestion = isResume
        ? `${state.question}\n\n【用户补充】\n${trimmed}`
        : trimmed

      setState((prev) =>
        isResume
          ? { ...prev, phase: 'clarifying', clarificationPrompt: '' }
          : startDeepResearch(trimmed),
      )
      if (isResume) {
        if (timerRef.current == null && startedAtRef.current != null) {
          timerRef.current = window.setInterval(() => {
            if (startedAtRef.current != null) {
              setElapsedMs(Date.now() - startedAtRef.current)
            }
          }, 1000)
        }
      } else {
        startTimer()
      }

      const controller = new AbortController()
      abortRef.current = controller

      try {
        await streamDeepResearch(conversationId, apiQuestion, controller.signal, (line) => {
          const chunk = parseAgentChunk(line)
          if (!chunk) return
          setState((prev) => reduceDeepResearchState(prev, chunk))
        })

        setState((prev) =>
          prev.phase === 'summarizing' || prev.report
            ? { ...prev, phase: 'complete' }
            : prev.phase === 'awaiting_clarification'
              ? prev
              : { ...prev, phase: 'complete' },
        )
      } catch (err) {
        if (controller.signal.aborted) {
          setState((prev) => ({ ...prev, phase: 'stopped' }))
        } else {
          const message =
            err instanceof Error && err.message.includes('in progress')
              ? t('deepResearch.error.inProgress')
              : t('deepResearch.error.generic')
          setError(message)
          setState((prev) => ({ ...prev, phase: 'error' }))
        }
      } finally {
        clearTimer()
        abortRef.current = null
      }
    },
    [conversationId, isStreaming, startTimer, clearTimer, state.phase, state.question],
  )

  const stopGeneration = useCallback(async () => {
    abortRef.current?.abort()
    try {
      await stopAgent(conversationId)
    } catch {
      // ignore
    }
    clearTimer()
    setState((prev) => ({ ...prev, phase: 'stopped' }))
  }, [conversationId, clearTimer])

  const reset = useCallback(() => {
    abortRef.current?.abort()
    clearTimer()
    setState(INITIAL_DEEP_RESEARCH_STATE)
    setError(null)
    setElapsedMs(0)
  }, [clearTimer])

  return {
    state,
    elapsedMs,
    isStreaming,
    error,
    sendQuestion,
    stopGeneration,
    reset,
    clearError: () => setError(null),
  }
}
