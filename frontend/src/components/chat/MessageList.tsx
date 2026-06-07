import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { Icon } from '../ui/Icon'
import type { ChatTurn } from '../../types'
import { AssistantMessage } from './AssistantMessage'
import { UserBubble } from './UserBubble'

interface MessageListProps {
  turns: ChatTurn[]
  isStreaming: boolean
  onRecommendSelect: (question: string) => void
}

export function MessageList({ turns, isStreaming, onRecommendSelect }: MessageListProps) {
  const { t } = useTranslation()
  const bottomRef = useRef<HTMLDivElement>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const userScrolledRef = useRef(false)

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    const handleScroll = () => {
      const distanceFromBottom =
        container.scrollHeight - container.scrollTop - container.clientHeight
      userScrolledRef.current = distanceFromBottom > 80
    }

    container.addEventListener('scroll', handleScroll)
    return () => container.removeEventListener('scroll', handleScroll)
  }, [])

  useEffect(() => {
    if (!userScrolledRef.current) {
      bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
    }
  }, [turns, isStreaming])

  if (turns.length === 0) {
    return (
      <div className="flex flex-1 flex-col items-center justify-center px-container-padding pb-40 text-center">
        <div className="ai-glow mb-6 flex h-16 w-16 items-center justify-center rounded-full border border-primary/30 bg-primary/20">
          <Icon name="travel_explore" filled className="text-3xl text-primary" />
        </div>
        <h2 className="bg-gradient-to-r from-primary to-secondary bg-clip-text font-headline-md text-headline-md font-bold text-transparent">
          {t('chat.emptyTitle')}
        </h2>
        <p className="mt-2 max-w-md font-body-md text-on-surface-variant">{t('chat.emptySubtitle')}</p>
      </div>
    )
  }

  return (
    <div ref={containerRef} className="flex-1 overflow-y-auto px-container-padding pb-44 pt-8 md:pb-40">
      <div className="mx-auto w-full max-w-chat space-y-8">
        {turns.map((turn) =>
          turn.role === 'user' ? (
            <UserBubble key={turn.id} content={turn.content} />
          ) : (
            <AssistantMessage
              key={turn.id}
              turn={turn}
              onRecommendSelect={onRecommendSelect}
              recommendDisabled={isStreaming}
            />
          ),
        )}
        <div ref={bottomRef} />
      </div>
    </div>
  )
}
