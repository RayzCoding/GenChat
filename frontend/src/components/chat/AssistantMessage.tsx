import type { ChatTurn } from '../../types'
import { Icon } from '../ui/Icon'
import { MarkdownContent } from './MarkdownContent'
import { RecommendChips } from './RecommendChips'
import { ReferenceTags } from './ReferenceTags'
import { ThinkingBlock } from './ThinkingBlock'

interface AssistantMessageProps {
  turn: ChatTurn
  onRecommendSelect?: (question: string) => void
  recommendDisabled?: boolean
}

export function AssistantMessage({
  turn,
  onRecommendSelect,
  recommendDisabled,
}: AssistantMessageProps) {
  const isStreaming = turn.status === 'streaming'

  return (
    <div className="flex justify-start gap-4 duration-700 animate-in fade-in slide-in-from-left-4">
      <div className="ai-glow flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-primary/30 bg-primary/20">
        <Icon name="auto_awesome" filled className="text-xl text-primary" />
      </div>

      <div className="max-w-[90%] flex-1 space-y-6">
        <ThinkingBlock
          thinking={turn.thinking ?? ''}
          toolCalls={turn.toolCalls}
          isStreaming={isStreaming && !turn.content}
        />

        {(turn.content || isStreaming) && (
          <div className="glass-panel ai-glow rounded-2xl border border-primary/10 p-8">
            <MarkdownContent content={turn.content} isStreaming={isStreaming} />
            {turn.references && turn.references.length > 0 && (
              <ReferenceTags references={turn.references} />
            )}
          </div>
        )}

        {turn.recommendations && turn.recommendations.length > 0 && onRecommendSelect && (
          <RecommendChips
            recommendations={turn.recommendations}
            onSelect={onRecommendSelect}
            disabled={recommendDisabled}
          />
        )}
      </div>
    </div>
  )
}
