import { useTranslation } from 'react-i18next'
import { Icon } from '../ui/Icon'

interface ThinkingBlockProps {
  thinking: string
  isStreaming?: boolean
}

export function ThinkingBlock({ thinking, isStreaming }: ThinkingBlockProps) {
  const { t } = useTranslation()

  if (!thinking && !isStreaming) return null

  return (
    <details className="group" open>
      <summary className="flex cursor-pointer list-none items-center gap-2 font-label-md text-tertiary-fixed transition-opacity hover:opacity-80">
        <Icon name="cyclone" className="text-sm" spin={isStreaming} />
        <span>{t('chat.thinkingProcess')}</span>
        <Icon
          name="expand_more"
          className="text-sm transition-transform group-open:rotate-180"
        />
      </summary>
      <div className="thinking-shimmer ml-6 mt-3 space-y-2 border-l-2 border-tertiary-fixed/20 pl-4 font-mono-code text-on-surface-variant">
        {thinking ? (
          <p className="whitespace-pre-wrap text-sm">{thinking}</p>
        ) : (
          isStreaming && <p className="text-sm">...</p>
        )}
      </div>
    </details>
  )
}
