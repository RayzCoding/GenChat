import { useCallback, useEffect, useState } from 'react'
import { getSessionDetail, listSessions, searchSessions } from '../services/sessionApi'
import type { SessionSummary } from '../types'
import { sessionMessagesToTurns } from '../utils/chat'
import type { ChatTurn } from '../types'

export function useSessions(pageSize = 30) {
  const [sessions, setSessions] = useState<SessionSummary[]>([])
  const [loading, setLoading] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')

  const refresh = useCallback(async () => {
    setLoading(true)
    try {
      const result = searchQuery.trim()
        ? await searchSessions(searchQuery.trim(), 1, pageSize)
        : await listSessions(1, pageSize)
      setSessions(result.items)
    } catch {
      setSessions([])
    } finally {
      setLoading(false)
    }
  }, [searchQuery, pageSize])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const loadSessionTurns = useCallback(async (conversationId: string): Promise<ChatTurn[]> => {
    const detail = await getSessionDetail(conversationId)
    return sessionMessagesToTurns(detail.messages)
  }, [])

  return {
    sessions,
    loading,
    searchQuery,
    setSearchQuery,
    refresh,
    loadSessionTurns,
  }
}
