import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation } from 'react-router-dom'
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
  const location = useLocation()
  const isHistoryPage = location.pathname.startsWith('/history')

  const [localQuery, setLocalQuery] = useState(searchQuery)
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  const pageTitle = isHistoryPage ? t('topbar.pageTitleHistory') : t('topbar.pageTitleChat')
  const searchPlaceholder = t('topbar.searchPlaceholder')

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
    <header
      className={
        isHistoryPage
          ? 'fixed left-0 right-0 top-0 z-50 flex h-header-height flex-shrink-0 items-center justify-between border-b border-outline-variant/10 bg-surface/70 px-container-padding shadow-sm backdrop-blur-xl md:left-sidebar-width'
          : 'sticky top-0 z-40 flex h-header-height flex-shrink-0 items-center justify-between border-b border-outline-variant/10 bg-surface/50 px-container-padding backdrop-blur-xl'
      }
    >
      <div className="flex items-center gap-3">
        <Icon name="menu" className="cursor-pointer md:hidden" />
        <span
          className={`font-headline-md text-headline-md font-bold tracking-tight ${
            isHistoryPage ? 'text-on-surface' : 'text-primary'
          }`}
        >
          {pageTitle}
        </span>
      </div>

      <div className="flex items-center gap-6">
        <div ref={containerRef} className="relative hidden lg:block">
          <form onSubmit={handleSubmit}>
            <input
              type="text"
              value={localQuery}
              onChange={(e) => {
                setLocalQuery(e.target.value)
                setOpen(true)
              }}
              onFocus={() => setOpen(true)}
              placeholder={searchPlaceholder}
              className="h-10 w-64 rounded-full border border-outline-variant/20 bg-surface-container px-4 pl-10 text-sm text-on-surface outline-none transition-all placeholder:text-on-surface-variant focus:border-tertiary focus:outline-none focus:ring-0"
            />
            <Icon
              name="search"
              className="pointer-events-none absolute left-3 top-2.5 text-sm text-on-surface-variant"
            />
          </form>

          {showDropdown && (
            <div className="absolute right-0 top-full z-50 mt-2 w-80 overflow-hidden rounded-xl border border-outline-variant/20 bg-surface-container-high shadow-2xl">
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

        <div className="flex gap-4">
          <button
            type="button"
            title={t('topbar.notifications')}
            className="cursor-pointer text-on-surface-variant transition-colors hover:text-primary"
          >
            <Icon name="notifications" />
          </button>
          <Link
            to="/history"
            title={t('topbar.history')}
            className={`cursor-pointer transition-colors ${
              isHistoryPage ? 'text-primary' : 'text-on-surface-variant hover:text-primary'
            }`}
          >
            <Icon name="history" />
          </Link>
          <button
            type="button"
            title={t('topbar.account')}
            className="cursor-pointer text-on-surface-variant transition-colors hover:text-primary"
          >
            <Icon name="account_circle" />
          </button>
        </div>
      </div>
    </header>
  )
}
