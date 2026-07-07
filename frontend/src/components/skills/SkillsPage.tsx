import { useCallback, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router-dom'
import { SKILLS_SUGGESTIONS } from '../../constants/suggestions'
import { useSessions } from '../../hooks/useSessions'
import { useSkillsStream } from '../../hooks/useSkillsStream'
import { createConversationId } from '../../utils/chat'
import { ConversationInput } from '../chat/ConversationInput'
import { AppShell } from '../layout/AppShell'
import { SkillsMessageList } from './SkillsMessageList'

export function SkillsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { conversationId: routeConversationId } = useParams<{ conversationId?: string }>()
  const [conversationId, setConversationId] = useState(
    () => routeConversationId ?? createConversationId(),
  )

  const {
    sessions,
    loading: sessionsLoading,
    searchQuery,
    setSearchQuery,
    refresh,
    loadSessionTurns,
  } = useSessions()

  const { turns, isStreaming, sendMessage, stopGeneration, setTurns, error } = useSkillsStream({
    conversationId,
    onSessionUpdated: () => void refresh(),
  })

  useEffect(() => {
    if (routeConversationId && routeConversationId !== conversationId) {
      setConversationId(routeConversationId)
    }
  }, [routeConversationId, conversationId])

  useEffect(() => {
    if (!routeConversationId || isStreaming) return

    let cancelled = false
    void loadSessionTurns(routeConversationId)
      .then((loaded) => {
        if (!cancelled) setTurns(loaded)
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
    navigate(`/skills/${newId}`, { replace: true })
  }, [navigate, setTurns])

  const handleSelectSession = useCallback(
    (id: string) => {
      if (isStreaming) return
      setConversationId(id)
      setTurns([])
      navigate(`/skills/${id}`)
    },
    [isStreaming, navigate, setTurns],
  )

  const handleSend = useCallback(
    (question: string, extras?: { fileId?: string }) => {
      if (!routeConversationId) {
        navigate(`/skills/${conversationId}`, { replace: true })
      }
      const fileId = extras?.fileId
      void sendMessage(question, t, fileId)
    },
    [conversationId, routeConversationId, navigate, sendMessage, t],
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
        <SkillsMessageList turns={turns} isStreaming={isStreaming} />
        {error && (
          <p className="px-container-padding pb-2 text-center font-label-md text-error">{error}</p>
        )}
        <ConversationInput
          key={conversationId}
          placeholder={t('skills.inputPlaceholder')}
          enterHint={t('input.enterHint')}
          disclaimer={t('input.disclaimer')}
          sendTitle={t('input.send')}
          attachTitle={t('input.attach')}
          onSend={handleSend}
          disabled={isStreaming}
          isStreaming={isStreaming}
          onStop={() => void stopGeneration()}
          layout="fixed"
          attachable
          persistAttachment
          suggestions={SKILLS_SUGGESTIONS}
          showSuggestions={turns.length === 0}
        />
      </div>
    </AppShell>
  )
}
