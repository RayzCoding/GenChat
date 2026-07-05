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

function displayTitle(ref: SearchResult): string {
  if (ref.title?.trim()) return ref.title.trim()
  if (ref.url?.trim()) {
    try {
      return new URL(ref.url).hostname.replace(/^www\./, '')
    } catch {
      return ref.url
    }
  }
  return 'Source'
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
        {references.map((ref, index) => {
          const title = displayTitle(ref)
          const href = ref.url?.trim() || undefined
          const className =
            'flex max-w-full items-center gap-2 rounded-lg border border-outline-variant/10 bg-surface-container px-3 py-1.5 transition-all hover:border-primary/40'

          const inner = (
            <>
              <div
                className={`flex h-5 w-5 shrink-0 items-center justify-center rounded text-[10px] font-semibold ${badgeColors[index % badgeColors.length]}`}
              >
                {index + 1}
              </div>
              <span className="truncate font-label-md text-xs">{title}</span>
            </>
          )

          if (!href) {
            return (
              <div key={`${title}-${index}`} className={className} title={ref.content}>
                {inner}
              </div>
            )
          }

          return (
            <a
              key={`${href}-${index}`}
              href={href}
              target="_blank"
              rel="noopener noreferrer"
              className={className}
              title={ref.content || title}
            >
              {inner}
            </a>
          )
        })}
      </div>
    </div>
  )
}
