import { useTranslation } from 'react-i18next'
import { Icon } from '../ui/Icon'

const FILTER_KEYS = ['all', 'aiChat', 'fileQa', 'ppt', 'research', 'skills'] as const
export type HistoryFilterKey = (typeof FILTER_KEYS)[number]

interface HistoryFiltersProps {
  activeFilter: HistoryFilterKey
  onFilterChange: (filter: HistoryFilterKey) => void
  multiSelectMode: boolean
  onToggleMultiSelect: () => void
}

export function HistoryFilters({
  activeFilter,
  onFilterChange,
  multiSelectMode,
  onToggleMultiSelect,
}: HistoryFiltersProps) {
  const { t } = useTranslation()

  return (
    <div className="flex flex-wrap gap-2">
      {FILTER_KEYS.map((key) => (
        <button
          key={key}
          type="button"
          onClick={() => onFilterChange(key)}
          className={`rounded-full px-5 py-2 font-label-md text-label-md transition-all ${
            activeFilter === key
              ? 'active-filter'
              : 'border border-outline-variant/20 bg-surface-container-low text-on-surface-variant hover:bg-surface-container-high'
          }`}
        >
          {t(`history.filters.${key}`)}
        </button>
      ))}
      <div className="mx-2 hidden h-8 w-px self-center bg-outline-variant/20 sm:block" />
      <button
        type="button"
        onClick={onToggleMultiSelect}
        className={`flex items-center gap-2 rounded-full border px-5 py-2 font-label-md text-label-md transition-all ${
          multiSelectMode
            ? 'active-filter'
            : 'border-outline-variant/20 bg-surface-container-low text-on-surface-variant hover:border-error/50 hover:text-error'
        }`}
      >
        <Icon name="checklist" className="text-[18px]" />
        {t('history.batchManage')}
      </button>
    </div>
  )
}
