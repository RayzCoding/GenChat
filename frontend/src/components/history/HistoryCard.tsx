import { useTranslation } from 'react-i18next'
import type { SessionSummary } from '../../types'
import {
  formatSessionDate,
  getSessionTypeMeta,
  truncateText,
} from '../../utils/sessionMeta'
import { Icon } from '../ui/Icon'

interface HistoryCardProps {
  session: SessionSummary
  multiSelectMode: boolean
  selected: boolean
  onSelectChange: (checked: boolean) => void
  onOpen: () => void
  locale: string
}

export function HistoryCard({
  session,
  multiSelectMode,
  selected,
  onSelectChange,
  onOpen,
  locale,
}: HistoryCardProps) {
  const { t } = useTranslation()
  const meta = getSessionTypeMeta(session.agentType)

  return (
    <div
      className={`glass-card group/card relative flex flex-col rounded-2xl p-6 ${
        multiSelectMode ? 'pl-[60px]' : ''
      }`}
    >
      <div className="absolute right-4 top-4 flex gap-1 opacity-0 transition-opacity group-hover/card:opacity-100">
        <button
          type="button"
          title={t('history.actions.pin')}
          className="rounded-lg p-1.5 text-on-surface-variant transition-colors hover:bg-surface-container-highest hover:text-primary"
        >
          <Icon name="push_pin" className="text-[18px]" />
        </button>
        <button
          type="button"
          title={t('history.actions.rename')}
          className="rounded-lg p-1.5 text-on-surface-variant transition-colors hover:bg-surface-container-highest hover:text-primary"
        >
          <Icon name="edit" className="text-[18px]" />
        </button>
        <button
          type="button"
          title={t('history.actions.delete')}
          className="rounded-lg p-1.5 text-on-surface-variant transition-colors hover:bg-surface-container-highest hover:text-error"
        >
          <Icon name="delete" className="text-[18px]" />
        </button>
      </div>

      {multiSelectMode && (
        <div className="absolute left-6 top-6 z-10">
          <input
            type="checkbox"
            checked={selected}
            onChange={(e) => onSelectChange(e.target.checked)}
            className="h-5 w-5 rounded border-outline-variant bg-transparent text-primary ring-offset-background focus:ring-primary"
          />
        </div>
      )}

      <div className="mb-4 flex items-center gap-3">
        <div
          className={`flex h-10 w-10 items-center justify-center rounded-xl ${meta.iconWrapClass}`}
        >
          <Icon name={meta.icon} filled />
        </div>
        <div>
          <span className={`text-[10px] font-bold uppercase tracking-wider ${meta.badgeClass}`}>
            {t(meta.labelKey)}
          </span>
          <p className="text-[12px] text-on-surface-variant/60">
            {formatSessionDate(session.updatedAt, locale)}
          </p>
        </div>
      </div>

      <h3
        className={`mb-3 font-headline-md text-[18px] font-bold leading-tight transition-colors ${meta.titleHoverClass}`}
      >
        {session.title || session.conversationId}
      </h3>

      <p className="mb-6 line-clamp-2 font-body-md text-sm text-on-surface-variant/80">
        {truncateText(session.lastQuestion, 50)}
      </p>

      <div className="mt-auto flex items-center justify-between">
        <span className="rounded border border-outline-variant/20 bg-surface-container-highest px-2 py-0.5 text-[10px] text-on-surface-variant">
          {session.messageCount} {t('history.rounds')}
        </span>
        <button
          type="button"
          onClick={onOpen}
          className={`flex items-center gap-1 font-label-md font-bold text-on-surface-variant transition-colors ${meta.actionHoverClass}`}
        >
          {t(meta.actionKey)}
          <Icon name={meta.actionIcon} className="text-[16px]" />
        </button>
      </div>
    </div>
  )
}
