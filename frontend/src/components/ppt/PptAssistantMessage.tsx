import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { ChatTurn } from '../../types'
import {
  detectPptPhase,
  extractFileUrl,
  extractPptTitle,
  extractSlideCount,
  thinkingPreview,
} from '../../utils/pptUi'
import { MarkdownContent } from '../chat/MarkdownContent'
import { Icon } from '../ui/Icon'
import { PptPreviewModal } from './PptPreviewModal'
import { PptProgressStrip } from './PptProgressStrip'
import { PptResultCard } from './PptResultCard'

interface PptAssistantMessageProps {
  turn: ChatTurn
  userQuestion?: string
  showProgress?: boolean
}

export function PptAssistantMessage({
  turn,
  userQuestion,
  showProgress = false,
}: PptAssistantMessageProps) {
  const { t } = useTranslation()
  const [previewOpen, setPreviewOpen] = useState(false)

  const isStreaming = turn.status === 'streaming'
  const thinking = turn.thinking ?? ''
  const phase = detectPptPhase(thinking, turn.content)
  const fileUrl = extractFileUrl(turn.content)
  const slideCount = extractSlideCount(turn.content)
  const title = useMemo(
    () => extractPptTitle(turn.content, userQuestion ? `${userQuestion.slice(0, 40)}.pptx` : 'presentation.pptx'),
    [turn.content, userQuestion],
  )

  const showThinkingLine = isStreaming || (thinking && !fileUrl)
  const previewText = thinkingPreview(thinking)

  return (
    <div className="flex flex-col items-start gap-4 duration-700 animate-in fade-in slide-in-from-left-4">
      <div className="flex w-full max-w-[85%] flex-col gap-2">
        {showThinkingLine && previewText && (
          <div className="ml-2 flex items-center gap-2 font-mono-code text-[11px] italic text-on-surface-variant">
            {isStreaming && (
              <Icon name="data_usage" className="animate-spin text-sm" />
            )}
            <span>
              {t('ppt.thinking')}: {previewText}
            </span>
          </div>
        )}

        {(turn.content || isStreaming) && (
          <div className="chat-bubble-ai rounded-2xl border border-outline-variant/20 bg-surface-container-high px-6 py-4 text-on-surface shadow-xl">
            <MarkdownContent content={turn.content} isStreaming={isStreaming && !fileUrl} />
          </div>
        )}

        {showProgress && isStreaming && !fileUrl && <PptProgressStrip phase={phase} />}
      </div>

      {fileUrl && (
        <PptResultCard
          title={title}
          slideCount={slideCount}
          fileUrl={fileUrl}
          onPreview={() => setPreviewOpen(true)}
        />
      )}

      {fileUrl && (
        <PptPreviewModal
          open={previewOpen}
          title={title}
          slideCount={slideCount}
          fileUrl={fileUrl}
          onClose={() => setPreviewOpen(false)}
        />
      )}
    </div>
  )
}
