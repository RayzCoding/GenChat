import { useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router-dom'
import { useChatStream } from '../../hooks/useChatStream'
import { useSessions } from '../../hooks/useSessions'
import { createConversationId, mergeSessionTurns } from '../../utils/chat'
import type { ChatTurn } from '../../types'
import { AppShell } from '../layout/AppShell'
import { ChatInput } from './ChatInput'
import { MessageList } from './MessageList'

export function ChatPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { conversationId: routeConversationId } = useParams<{ conversationId?: string }>()
  const [conversationId, setConversationId] = useState(
    () => routeConversationId ?? createConversationId(),
  )
  const skipHistoryLoadRef = useRef(false)

  const {
    sessions,
    loading: sessionsLoading,
    searchQuery,
    setSearchQuery,
    refresh,
    loadSessionTurns,
  } = useSessions()

  const turnsRef = useRef<ChatTurn[]>([])
  const {
    turns,
    isStreaming,
    sendMessage,
    stopGeneration,
    setTurns,
  } = useChatStream({
    conversationId,
    onSessionUpdated: () => void refresh(),
    onStreamComplete: () => {
      skipHistoryLoadRef.current = true
    },
  })
  turnsRef.current = turns

  useEffect(() => {
    if (routeConversationId && routeConversationId !== conversationId) {
      setConversationId(routeConversationId)
    }
  }, [routeConversationId, conversationId])

  useEffect(() => {
    if (!routeConversationId || isStreaming) return
    if (skipHistoryLoadRef.current) {
      skipHistoryLoadRef.current = false
      return
    }

    let cancelled = false
    void loadSessionTurns(routeConversationId)
      .then((loaded) => {
        if (!cancelled) {
          setTurns(mergeSessionTurns(turnsRef.current, loaded))
        }
      })
      .catch(() => {
        if (!cancelled) setTurns([])
      })

    return () => {
      cancelled = true
    }
  }, [routeConversationId, isStreaming, loadSessionTurns, setTurns])

  const handleNewChat = useCallback(() => {
    const newId = createConversationId()
    setConversationId(newId)
    setTurns([])
    navigate(`/chat/${newId}`, { replace: true })
  }, [navigate, setTurns])

  const handleSelectSession = useCallback(
    (id: string) => {
      if (isStreaming) return
      setConversationId(id)
      setTurns([])
      navigate(`/chat/${id}`)
    },
    [isStreaming, navigate, setTurns],
  )

  const handleSend = useCallback(
    (question: string) => {
      if (!routeConversationId) {
        navigate(`/chat/${conversationId}`, { replace: true })
      }
      void sendMessage(question, t)
    },
    [conversationId, routeConversationId, navigate, sendMessage, t],
  )

  const handleRecommendSelect = useCallback(
    (question: string) => {
      handleSend(question)
    },
    [handleSend],
  )

  return (
    <AppShell
      onNewChat={handleNewChat}
      searchQuery={searchQuery}
      onSearchChange={setSearchQuery}
      onSearchSubmit={() => void refresh()}
      searchResults={sessions}
      onSelectSession={handleSelectSession}
      sessionsLoading={sessionsLoading}
    >
      <div className="flex min-h-0 flex-1 flex-col">
        <MessageList
          turns={turns}
          isStreaming={isStreaming}
          onRecommendSelect={handleRecommendSelect}
        />
        <ChatInput
          onSend={handleSend}
          disabled={isStreaming}
          isStreaming={isStreaming}
          onStop={() => void stopGeneration()}
        />
      </div>
    </AppShell>
  )
}
