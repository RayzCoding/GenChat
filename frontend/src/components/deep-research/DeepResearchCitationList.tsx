import type { SearchResult } from '../../types'

const badgeColors = [
  'bg-primary/20 text-primary',
  'bg-tertiary/20 text-tertiary',
  'bg-secondary/20 text-secondary',
]

function badgeLetter(title?: string, url?: string): string {
  const source = title || url || '?'
  return source.slice(0, 1).toUpperCase()
}

function displayDomain(url: string): string {
  try {
    return new URL(url).hostname.replace(/^www\./, '')
  } catch {
    return url
  }
}

interface DeepResearchCitationListProps {
  references: SearchResult[]
  className?: string
}

export function DeepResearchCitationList({
  references,
  className = '',
}: DeepResearchCitationListProps) {
  if (!references.length) {
    return (
      <p className={`font-label-md text-on-surface-variant opacity-60 ${className}`}>
        —
      </p>
    )
  }

  return (
    <div
      className={`custom-scrollbar space-y-3 overflow-y-auto pr-2 ${className}`}
    >
      {references.map((ref, index) => (
        <a
          key={`${ref.url}-${index}`}
          href={ref.url}
          target="_blank"
          rel="noopener noreferrer"
          className="group block cursor-pointer rounded-lg border border-outline-variant/20 bg-surface-container-high p-3 transition-colors hover:border-tertiary/50"
        >
          <div className="mb-1 flex items-center gap-2">
            <div
              className={`flex h-4 w-4 items-center justify-center rounded text-[10px] ${badgeColors[index % badgeColors.length]}`}
            >
              {badgeLetter(ref.title, ref.url)}
            </div>
            <span className="truncate font-label-md font-bold text-on-surface">
              {ref.title || ref.url}
            </span>
          </div>
          {ref.content && (
            <p className="line-clamp-2 text-[11px] text-on-surface-variant">{ref.content}</p>
          )}
          <span className="mt-2 block text-[9px] text-primary/70 group-hover:underline">
            {displayDomain(ref.url)}
          </span>
        </a>
      ))}
    </div>
  )
}
