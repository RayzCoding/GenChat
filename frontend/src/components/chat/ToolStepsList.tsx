import type { ToolCallStep } from '../../types'
import { formatToolLabel } from '../../utils/toolSteps'
import { Icon } from '../ui/Icon'

interface ToolStepsListProps {
  steps: ToolCallStep[]
}

export function ToolStepsList({ steps }: ToolStepsListProps) {
  if (!steps.length) return null

  return (
    <div className="space-y-1.5">
      {steps.map((step) => {
        const failed = step.status === 'failed'
        const running = step.status === 'running'
        const label = formatToolLabel(step)

        return (
          <div
            key={step.toolCallId}
            className={`flex items-start gap-2 ${
              failed ? 'text-error' : running ? 'text-on-surface' : 'text-on-surface-variant'
            }`}
          >
            {failed ? (
              <Icon name="cancel" className="mt-0.5 shrink-0 text-base text-error" />
            ) : running ? (
              <Icon name="pending" className="mt-0.5 shrink-0 animate-spin text-base text-primary" />
            ) : (
              <Icon name="check_circle" filled className="mt-0.5 shrink-0 text-base text-tertiary" />
            )}
            <span className="whitespace-pre-wrap break-all text-sm">{label}</span>
          </div>
        )
      })}
    </div>
  )
}
