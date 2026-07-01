import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import type { ChatTurn } from '../../types'
import { Icon } from '../ui/Icon'
import { UserBubble } from '../chat/UserBubble'
import { SkillsAssistantMessage } from './SkillsAssistantMessage'

interface SkillsMessageListProps {
  turns: ChatTurn[]
  isStreaming: boolean
}

export function SkillsMessageList({ turns, isStreaming }: SkillsMessageListProps) {
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
        <div className="ai-glow mb-6 flex h-16 w-16 items-center justify-center rounded-2xl border border-secondary-container/30 bg-secondary-container/20">
          <Icon name="extension" filled className="text-3xl text-secondary" />
        </div>
        <h2 className="font-headline-md text-headline-md font-bold text-on-surface">
          {t('skills.emptyTitle')}
        </h2>
        <p className="mt-2 max-w-md font-body-md text-on-surface-variant">{t('skills.emptySubtitle')}</p>
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
            <SkillsAssistantMessage key={turn.id} turn={turn} />
          ),
        )}
        <div ref={bottomRef} />
      </div>
    </div>
  )
}
