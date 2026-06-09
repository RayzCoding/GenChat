import { useTranslation } from 'react-i18next'
import { parseSkillsThinkingSteps } from '../../utils/skillsUi'
import { Icon } from '../ui/Icon'

interface SkillsThinkingPanelProps {
  thinking: string
  isStreaming?: boolean
}

export function SkillsThinkingPanel({ thinking, isStreaming }: SkillsThinkingPanelProps) {
  const { t } = useTranslation()
  const steps = parseSkillsThinkingSteps(thinking)

  if (!thinking && !isStreaming) return null

  return (
    <details className="group w-full" open={isStreaming || !!thinking}>
      <summary className="flex cursor-pointer list-none items-center gap-2 rounded-xl border border-secondary-container/30 bg-secondary-container/10 px-4 py-2.5 font-label-md text-on-secondary-container transition-opacity hover:opacity-90">
        <Icon name="psychology" className="text-sm" spin={isStreaming && !thinking} />
        <span>{t('skills.thinkingProcess')}</span>
        <Icon
          name="expand_more"
          className="ml-auto text-sm transition-transform group-open:rotate-180"
        />
      </summary>

      <div className="mt-2 space-y-1.5 rounded-xl border border-outline-variant/10 bg-surface-container-low/80 p-4 font-mono-code text-sm">
        {steps.length > 0 ? (
          steps.map((step, index) => (
            <div
              key={`${step.label}-${index}`}
              className={`flex items-start gap-2 ${
                step.failed ? 'text-error' : step.kind === 'tool' ? 'text-on-surface' : 'text-on-surface-variant'
              }`}
            >
              {step.kind === 'tool' ? (
                step.failed ? (
                  <Icon name="cancel" className="mt-0.5 shrink-0 text-base text-error" />
                ) : step.success ? (
                  <Icon name="check_circle" filled className="mt-0.5 shrink-0 text-base text-tertiary" />
                ) : (
                  <Icon name="pending" className="mt-0.5 shrink-0 animate-spin text-base text-primary" />
                )
              ) : (
                <span className="mt-0.5 shrink-0 text-on-surface-variant">•</span>
              )}
              <span className="whitespace-pre-wrap break-all">{step.label}</span>
            </div>
          ))
        ) : (
          isStreaming && (
            <div className="flex items-center gap-2 text-on-surface-variant">
              <Icon name="pending" className="animate-spin text-sm" />
              <span>{t('skills.thinkingRunning')}</span>
            </div>
          )
        )}
      </div>
    </details>
  )
}
