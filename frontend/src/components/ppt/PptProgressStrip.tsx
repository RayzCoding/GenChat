import { useTranslation } from 'react-i18next'
import type { PptPhase } from '../../utils/pptUi'
import { getProgressSteps } from '../../utils/pptUi'
import { Icon } from '../ui/Icon'

interface PptProgressStripProps {
  phase: PptPhase
}

export function PptProgressStrip({ phase }: PptProgressStripProps) {
  const { t } = useTranslation()
  const { outline, generating } = getProgressSteps(phase)

  if (phase === 'idle' || phase === 'complete' || phase === 'awaiting_brief' || phase === 'failed') {
    return null
  }

  return (
    <div className="flex items-center gap-4 px-2 py-4">
      <div className="flex items-center gap-3 font-label-md text-on-surface-variant">
        <div className="flex items-center gap-1.5">
          {outline ? (
            <Icon name="check_circle" filled className="text-lg text-tertiary" />
          ) : (
            <div className="h-5 w-5 rounded-full border-2 border-outline-variant/40" />
          )}
          <span className={outline ? 'text-on-surface opacity-60' : ''}>{t('ppt.progress.outline')}</span>
        </div>
        <div className="h-px w-8 bg-outline-variant/30" />
        <div className="flex items-center gap-1.5">
          {generating ? (
            <div className="h-5 w-5 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          ) : (
            <div className="h-5 w-5 rounded-full border-2 border-outline-variant/40" />
          )}
          <span className={generating ? 'font-bold text-primary' : ''}>
            {t('ppt.progress.generating')}
          </span>
        </div>
      </div>
    </div>
  )
}
