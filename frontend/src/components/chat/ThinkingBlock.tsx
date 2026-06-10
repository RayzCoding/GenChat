import { useTranslation } from 'react-i18next'
import type { ToolCallStep } from '../../types'
import { Icon } from '../ui/Icon'
import { ToolStepsList } from './ToolStepsList'

interface ThinkingBlockProps {
  thinking: string
  toolCalls?: ToolCallStep[]
  isStreaming?: boolean
}

export function ThinkingBlock({ thinking, toolCalls, isStreaming }: ThinkingBlockProps) {
  const { t } = useTranslation()
  const hasToolCalls = (toolCalls?.length ?? 0) > 0

  if (!thinking && !hasToolCalls && !isStreaming) return null

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
        {hasToolCalls && <ToolStepsList steps={toolCalls!} />}
        {thinking ? (
          <p className="whitespace-pre-wrap text-sm">{thinking}</p>
        ) : (
          isStreaming && !hasToolCalls && <p className="text-sm">...</p>
        )}
      </div>
    </details>
  )
}
