import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router-dom'
import { PPT_SUGGESTIONS } from '../../constants/suggestions'
import { usePptStream } from '../../hooks/usePptStream'
import { useSessions } from '../../hooks/useSessions'
import { createConversationId } from '../../utils/chat'
import { detectPptPhase } from '../../utils/pptUi'
import { ConversationInput } from '../chat/ConversationInput'
import { AppShell } from '../layout/AppShell'
import { Icon } from '../ui/Icon'
import { PptAssistantMessage } from './PptAssistantMessage'
import { PptUserBubble } from './PptUserBubble'

export function PptGenPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { conversationId: routeConversationId } = useParams<{ conversationId?: string }>()
  const scrollRef = useRef<HTMLDivElement>(null)

  const [conversationId, setConversationId] = useState(
    () => routeConversationId ?? createConversationId(),
  )

  const {
    searchQuery,
    setSearchQuery,
    refresh,
    loadSessionTurns,
  } = useSessions()

  const {
    turns,
    isStreaming,
    sendMessage,
    stopGeneration,
    setTurns,
    error,
  } = usePptStream({
    conversationId,
    onSessionUpdated: () => void refresh(),
  })

  useEffect(() => {
    if (routeConversationId && routeConversationId !== conversationId) {
      setConversationId(routeConversationId)
    }
  }, [routeConversationId, conversationId])

  useEffect(() => {
    if (!routeConversationId) return

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
  }, [routeConversationId, loadSessionTurns, setTurns])

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' })
  }, [turns, isStreaming])

  const lastAssistant = [...turns].reverse().find((turn) => turn.role === 'assistant')
  const awaitingBrief = lastAssistant
    ? detectPptPhase(lastAssistant.thinking ?? '', lastAssistant.content) === 'awaiting_brief'
    : false

  const inputPlaceholderKey = awaitingBrief ? 'ppt.inputPlaceholderBrief' : 'ppt.inputPlaceholder'

  const handleNewChat = useCallback(() => {
    const newId = createConversationId()
    setConversationId(newId)
    setTurns([])
    navigate(`/ppt/${newId}`, { replace: true })
  }, [navigate, setTurns])

  const handleSend = useCallback(
    (question: string) => {
      if (!routeConversationId) {
        navigate(`/ppt/${conversationId}`, { replace: true })
      }
      void sendMessage(question, t)
    },
    [conversationId, routeConversationId, navigate, sendMessage, t],
  )

  const pairs = useMemo(() => {
    const result: Array<{ user?: (typeof turns)[0]; assistant?: (typeof turns)[0] }> = []
    for (let i = 0; i < turns.length; i++) {
      const turn = turns[i]
      if (turn.role === 'user') {
        const next = turns[i + 1]
        result.push({
          user: turn,
          assistant: next?.role === 'assistant' ? next : undefined,
        })
        if (next?.role === 'assistant') i++
      }
    }
    return result
  }, [turns])

  return (
    <AppShell
      onNewChat={handleNewChat}
      searchQuery={searchQuery}
      onSearchChange={setSearchQuery}
      onSearchSubmit={() => undefined}
    >
      <div className="relative flex min-h-0 flex-1 flex-col overflow-hidden">
        <div
          ref={scrollRef}
          className="custom-scrollbar flex-1 overflow-y-auto p-container-padding pb-36"
        >
          <div className="mx-auto max-w-4xl space-y-8">
            {turns.length === 0 && (
              <div className="flex flex-col items-center justify-center py-16 text-center">
                <div className="mb-6 flex h-16 w-16 items-center justify-center rounded-2xl bg-primary/10 text-primary">
                  <Icon name="present_to_all" className="text-3xl" />
                </div>
                <h2 className="mb-2 font-headline-md text-on-surface">{t('ppt.welcomeTitle')}</h2>
                <p className="max-w-md font-body-md text-on-surface-variant">
                  {t('ppt.welcomeHint')}
                </p>
              </div>
            )}

            {pairs.map(({ user, assistant }) => (
              <div key={user?.id ?? assistant?.id} className="space-y-8">
                {user && <PptUserBubble content={user.content} />}
                {assistant && (
                  <PptAssistantMessage
                    turn={assistant}
                    userQuestion={user?.content}
                    showProgress={
                      isStreaming && assistant.id === lastAssistant?.id && assistant.status === 'streaming'
                    }
                  />
                )}
              </div>
            ))}

            {error && <p className="text-center font-label-md text-error">{error}</p>}
          </div>
        </div>

        <ConversationInput
          placeholder={t(inputPlaceholderKey)}
          disclaimer={t('input.disclaimer')}
          sendTitle={t('input.send')}
          onSend={handleSend}
          disabled={isStreaming}
          isStreaming={isStreaming}
          onStop={() => void stopGeneration()}
          layout="fixed"
          maxWidthClass="max-w-4xl"
          suggestions={PPT_SUGGESTIONS}
          showSuggestions={turns.length === 0}
        />
      </div>
    </AppShell>
  )
}
