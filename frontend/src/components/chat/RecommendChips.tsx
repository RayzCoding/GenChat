import { useTranslation } from 'react-i18next'

interface RecommendChipsProps {
  recommendations: string[]
  onSelect: (question: string) => void
  disabled?: boolean
}

const chipStyles = [
  'border-primary/20 bg-primary/5 text-primary hover:bg-primary/10',
  'border-secondary/20 bg-secondary/5 text-secondary hover:bg-secondary/10',
  'border-tertiary/20 bg-tertiary/5 text-tertiary hover:bg-tertiary/10',
]

export function RecommendChips({ recommendations, onSelect, disabled }: RecommendChipsProps) {
  const { t } = useTranslation()

  if (!recommendations.length) return null

  return (
    <div className="flex flex-col gap-3">
      <p className="px-1 font-label-md text-on-surface-variant">{t('chat.recommendations')}</p>
      <div className="flex flex-wrap gap-2">
        {recommendations.map((question, index) => (
          <button
            key={question}
            type="button"
            disabled={disabled}
            onClick={() => onSelect(question)}
            className={`rounded-full border px-4 py-2 text-sm font-label-md transition-all active:scale-95 disabled:opacity-50 ${chipStyles[index % chipStyles.length]}`}
          >
            {question}
          </button>
        ))}
      </div>
    </div>
  )
}
