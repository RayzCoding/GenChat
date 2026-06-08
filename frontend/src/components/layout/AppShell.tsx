import type { ReactNode } from 'react'
import { useLocation } from 'react-router-dom'
import type { SessionSummary } from '../../types'
import { MobileNav } from './MobileNav'
import { Sidebar } from './Sidebar'
import { TopBar } from './TopBar'

interface AppShellProps {
  children: ReactNode
  onNewChat: () => void
  searchQuery: string
  onSearchChange: (query: string) => void
  onSearchSubmit: () => void
  searchResults?: SessionSummary[]
  onSelectSession?: (conversationId: string) => void
  sessionsLoading?: boolean
}

export function AppShell({
  children,
  onNewChat,
  searchQuery,
  onSearchChange,
  onSearchSubmit,
  searchResults,
  onSelectSession,
  sessionsLoading,
}: AppShellProps) {
  const location = useLocation()
  const isHistoryPage = location.pathname.startsWith('/history')

  return (
    <div className="min-h-screen bg-surface">
      <Sidebar onNewChat={onNewChat} />
      <main
        className={`relative flex min-h-screen flex-col overflow-hidden md:ml-sidebar-width ${
          isHistoryPage ? 'md:pt-header-height' : ''
        }`}
      >
        <TopBar
          searchQuery={searchQuery}
          onSearchChange={onSearchChange}
          onSearchSubmit={onSearchSubmit}
          searchResults={searchResults}
          onSelectSession={onSelectSession}
          sessionsLoading={sessionsLoading}
        />
        {children}
        <MobileNav />
      </main>
    </div>
  )
}
