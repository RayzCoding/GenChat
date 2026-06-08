import { useCallback, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useSessions } from '../../hooks/useSessions'
import { createConversationId } from '../../utils/chat'
import { AppShell } from '../layout/AppShell'
import { HistoryCard } from './HistoryCard'
import { HistoryFilters, type HistoryFilterKey } from './HistoryFilters'
import { SelectionBar } from './SelectionBar'
import { Icon } from '../ui/Icon'

export function HistoryPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const {
    sessions,
    loading,
    searchQuery,
    setSearchQuery,
    refresh,
  } = useSessions(100)

  const [activeFilter, setActiveFilter] = useState<HistoryFilterKey>('all')
  const [multiSelectMode, setMultiSelectMode] = useState(false)
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())

  const handleNewChat = useCallback(() => {
    const newId = createConversationId()
    navigate(`/chat/${newId}`)
  }, [navigate])

  const handleOpenSession = useCallback(
    (conversationId: string) => {
      navigate(`/chat/${conversationId}`)
    },
    [navigate],
  )

  const toggleMultiSelect = useCallback(() => {
    setMultiSelectMode((prev) => {
      if (prev) {
        setSelectedIds(new Set())
      }
      return !prev
    })
  }, [])

  const handleSelectChange = useCallback((conversationId: string, checked: boolean) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (checked) {
        next.add(conversationId)
      } else {
        next.delete(conversationId)
      }
      return next
    })
  }, [])

  const displayedSessions = useMemo(() => sessions, [sessions])

  return (
    <AppShell
      onNewChat={handleNewChat}
      searchQuery={searchQuery}
      onSearchChange={setSearchQuery}
      onSearchSubmit={() => void refresh()}
      searchResults={sessions}
      onSelectSession={handleOpenSession}
      sessionsLoading={loading}
    >
      <div className="flex min-h-0 flex-1 flex-col overflow-y-auto pb-28">
        <div className="mx-auto w-full max-w-7xl p-8">
          <div className="mb-10 flex flex-col justify-between gap-6 md:flex-row md:items-end">
            <div>
              <h2 className="mb-2 font-headline-lg text-headline-lg font-bold">
                {t('history.title')}
              </h2>
              <p className="font-body-md text-on-surface-variant">{t('history.subtitle')}</p>
            </div>
            <HistoryFilters
              activeFilter={activeFilter}
              onFilterChange={setActiveFilter}
              multiSelectMode={multiSelectMode}
              onToggleMultiSelect={toggleMultiSelect}
            />
          </div>

          {loading && (
            <p className="py-12 text-center text-on-surface-variant">{t('history.loading')}</p>
          )}

          {!loading && (
            <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
              {displayedSessions.map((session) => (
                <HistoryCard
                  key={session.conversationId}
                  session={session}
                  multiSelectMode={multiSelectMode}
                  selected={selectedIds.has(session.conversationId)}
                  onSelectChange={(checked) =>
                    handleSelectChange(session.conversationId, checked)
                  }
                  onOpen={() => handleOpenSession(session.conversationId)}
                  locale={i18n.language}
                />
              ))}

              <button
                type="button"
                onClick={handleNewChat}
                className="group flex cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed border-outline-variant/20 p-8 opacity-40 transition-opacity hover:opacity-100"
              >
                <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-surface-container-low transition-transform group-hover:scale-110">
                  <Icon name="add" className="text-[32px]" />
                </div>
                <p className="font-label-md text-label-md font-bold">{t('history.newExplore')}</p>
              </button>
            </div>
          )}

          {!loading && displayedSessions.length === 0 && (
            <p className="py-8 text-center text-on-surface-variant">{t('session.noHistory')}</p>
          )}
        </div>
      </div>

      <SelectionBar
        visible={multiSelectMode}
        selectedCount={selectedIds.size}
        onCancel={toggleMultiSelect}
      />
    </AppShell>
  )
}
