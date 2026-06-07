import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { SessionSummary } from '../../types'
import { Icon } from '../ui/Icon'

interface TopBarProps {
  searchQuery: string
  onSearchChange: (query: string) => void
  onSearchSubmit: () => void
  searchResults?: SessionSummary[]
  onSelectSession?: (conversationId: string) => void
  sessionsLoading?: boolean
}

export function TopBar({
  searchQuery,
  onSearchChange,
  onSearchSubmit,
  searchResults = [],
  onSelectSession,
  sessionsLoading,
}: TopBarProps) {
  const { t } = useTranslation()
  const [localQuery, setLocalQuery] = useState(searchQuery)
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    setLocalQuery(searchQuery)
  }, [searchQuery])

  useEffect(() => {
    const handleClick = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [])

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    onSearchChange(localQuery)
    onSearchSubmit()
    setOpen(true)
  }

  const showDropdown = open && localQuery.trim().length > 0

  return (
    <header className="sticky top-0 z-40 flex h-16 items-center justify-between border-b border-outline-variant/10 bg-surface/50 px-container-padding backdrop-blur-xl">
      <div className="flex items-center gap-4">
        <Icon name="menu" className="cursor-pointer md:hidden" />
        <span className="bg-gradient-to-r from-primary to-secondary bg-clip-text font-headline-lg text-headline-lg font-bold text-transparent md:hidden">
          GenChat
        </span>
      </div>

      <div ref={containerRef} className="relative hidden flex-1 justify-center lg:flex">
        <form onSubmit={handleSubmit} className="w-64">
          <div className="flex items-center rounded-full border border-outline-variant/10 bg-surface-container px-4 py-2">
            <Icon name="search" className="text-sm text-on-surface-variant" />
            <input
              type="search"
              value={localQuery}
              onChange={(e) => {
                setLocalQuery(e.target.value)
                setOpen(true)
              }}
              onFocus={() => setOpen(true)}
              placeholder={t('topbar.searchPlaceholder')}
              className="w-full border-none bg-transparent text-sm text-on-surface placeholder:text-on-surface-variant focus:ring-0"
            />
          </div>
        </form>

        {showDropdown && (
          <div className="absolute top-full z-50 mt-2 w-80 overflow-hidden rounded-xl border border-outline-variant/20 bg-surface-container-high shadow-2xl">
            {sessionsLoading && (
              <p className="px-4 py-3 text-sm text-on-surface-variant">...</p>
            )}
            {!sessionsLoading && searchResults.length === 0 && (
              <p className="px-4 py-3 text-sm text-on-surface-variant">{t('session.noHistory')}</p>
            )}
            {searchResults.map((session) => (
              <button
                key={session.conversationId}
                type="button"
                onClick={() => {
                  onSelectSession?.(session.conversationId)
                  setOpen(false)
                }}
                className="block w-full truncate px-4 py-3 text-left text-sm text-on-surface transition hover:bg-surface-container-highest"
              >
                {session.title || session.conversationId}
              </button>
            ))}
          </div>
        )}
      </div>

      <div className="flex items-center gap-3">
        <Icon
          name="notifications"
          className="cursor-pointer text-on-surface-variant transition-colors hover:text-primary"
        />
        <Icon
          name="history"
          className="cursor-pointer text-on-surface-variant transition-colors hover:text-primary"
        />
        <Icon
          name="account_circle"
          className="cursor-pointer text-on-surface-variant transition-colors hover:text-primary"
        />
      </div>
    </header>
  )
}
