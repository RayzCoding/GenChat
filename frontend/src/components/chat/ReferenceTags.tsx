import { useTranslation } from 'react-i18next'
import type { SearchResult } from '../../types'
import { Icon } from '../ui/Icon'

interface ReferenceTagsProps {
  references: SearchResult[]
}

const badgeColors = [
  'bg-blue-500/20 text-blue-300',
  'bg-green-500/20 text-green-300',
  'bg-primary/20 text-primary',
  'bg-secondary/20 text-secondary',
]

function badgeLabel(title?: string, url?: string): string {
  const source = title || url || '?'
  return source.slice(0, 2).toUpperCase()
}

export function ReferenceTags({ references }: ReferenceTagsProps) {
  const { t } = useTranslation()

  if (!references.length) return null

  return (
    <div className="mt-8 border-t border-outline-variant/20 pt-6">
      <h3 className="mb-3 flex items-center gap-2 font-label-md text-label-md text-on-surface-variant">
        <Icon name="link" className="text-sm" />
        {t('chat.references')}
      </h3>
      <div className="flex flex-wrap gap-3">
        {references.map((ref, index) => (
          <a
            key={`${ref.url}-${index}`}
            href={ref.url}
            target="_blank"
            rel="noopener noreferrer"
            className="flex items-center gap-2 rounded-lg border border-outline-variant/10 bg-surface-container px-3 py-1.5 transition-all hover:border-primary/40"
            title={ref.content}
          >
            <div
              className={`flex h-4 w-4 items-center justify-center rounded text-[10px] ${badgeColors[index % badgeColors.length]}`}
            >
              {badgeLabel(ref.title, ref.url)}
            </div>
            <span className="font-label-md text-xs">{ref.title || ref.url}</span>
          </a>
        ))}
      </div>
    </div>
  )
}
