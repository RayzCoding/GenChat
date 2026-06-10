import { useTranslation } from 'react-i18next'
import type { ChatTurn } from '../../types'
import { extractFileLinks } from '../../utils/skillsUi'
import { MarkdownContent } from '../chat/MarkdownContent'
import { Icon } from '../ui/Icon'
import { SkillsThinkingPanel } from './SkillsThinkingPanel'

interface SkillsAssistantMessageProps {
  turn: ChatTurn
}

export function SkillsAssistantMessage({ turn }: SkillsAssistantMessageProps) {
  const { t } = useTranslation()
  const isStreaming = turn.status === 'streaming'
  const thinking = turn.thinking ?? ''
  const fileLinks = extractFileLinks(turn.content)
  const showSuccess = !isStreaming && /成功|successfully|created|完成/i.test(turn.content)

  return (
    <div className="flex flex-col items-start gap-3 duration-700 animate-in fade-in slide-in-from-left-4">
      <div className="flex w-full max-w-chat flex-col gap-3">
        <SkillsThinkingPanel
          thinking={thinking}
          toolCalls={turn.toolCalls}
          isStreaming={isStreaming && !turn.content}
        />

        {(turn.content || isStreaming) && (
          <div className="glass-panel w-full rounded-2xl border border-outline-variant/20 p-6 shadow-xl">
            {showSuccess && (
              <div className="mb-4 flex items-center gap-2 font-label-md text-tertiary">
                <Icon name="check_circle" filled className="text-lg" />
                <span>{t('skills.taskComplete')}</span>
              </div>
            )}

            <MarkdownContent content={turn.content} isStreaming={isStreaming} />

            {fileLinks.length > 0 && !isStreaming && (
              <div className="mt-4 space-y-2 border-t border-outline-variant/10 pt-4">
                {fileLinks.map((link) => {
                  const isUrl = link.startsWith('http')
                  return isUrl ? (
                    <a
                      key={link}
                      href={link}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="flex items-center gap-2 font-label-md text-primary transition-colors hover:text-primary-fixed"
                    >
                      <Icon name="description" className="text-base" />
                      {link.split('/').pop() ?? link}
                    </a>
                  ) : (
                    <div key={link} className="flex items-center gap-2 font-label-md text-primary">
                      <Icon name="description" className="text-base" />
                      {link}
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
