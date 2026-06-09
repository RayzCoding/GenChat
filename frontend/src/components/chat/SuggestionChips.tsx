import { useTranslation } from 'react-i18next'
import { Icon } from '../ui/Icon'

export interface SuggestionConfig {
  icon: string
  labelKey: string
  promptKey: string
}

interface SuggestionChipsProps {
  items: SuggestionConfig[]
  onSelect: (prompt: string) => void
  disabled?: boolean
}

export function SuggestionChips({ items, onSelect, disabled }: SuggestionChipsProps) {
  const { t } = useTranslation()

  return (
    <div className="mb-3 flex flex-wrap justify-center gap-2">
      {items.map((item) => {
        const label = t(item.labelKey)
        const prompt = t(item.promptKey)
        return (
          <button
            key={item.labelKey}
            type="button"
            title={prompt}
            disabled={disabled}
            onClick={() => onSelect(prompt)}
            className="flex max-w-full items-center gap-1.5 rounded-full border border-outline-variant/20 bg-surface-container-high/80 px-3 py-1.5 font-label-md text-on-surface-variant transition-all hover:border-primary/30 hover:text-primary disabled:opacity-50"
          >
            <Icon name={item.icon} className="shrink-0 text-[16px] opacity-70" />
            <span className="truncate">{label}</span>
          </button>
        )
      })}
    </div>
  )
}
