import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useFileStream } from '../../hooks/useFileStream'
import type { FileInfoDto } from '../../services/fileApi'
import type { ChatTurn } from '../../types'
import { deleteFile, listFiles, uploadFile } from '../../services/fileApi'
import { getFileSessionDetail } from '../../services/sessionApi'
import { createConversationId, mergeSessionTurns, sessionMessagesToTurns } from '../../utils/chat'
import {
  createFileConversationId,
  resolveFileConversationId,
  storeFileConversationId,
} from '../../utils/fileConversation'
import { fileStatusLabel, formatFileSize, getFileIcon, isFileReady } from '../../utils/fileMeta'
import { AppShell } from '../layout/AppShell'
import { ConversationInput } from '../chat/ConversationInput'
import { MessageList } from '../chat/MessageList'
import { Icon } from '../ui/Icon'

export function FileQaPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [searchQuery, setSearchQuery] = useState('')
  const [conversationId, setConversationId] = useState(createConversationId)
  const [files, setFiles] = useState<FileInfoDto[]>([])
  const [activeFileId, setActiveFileId] = useState<number | null>(null)
  const [uploading, setUploading] = useState(false)
  const [uploadError, setUploadError] = useState<string | null>(null)
  const [filesLoading, setFilesLoading] = useState(true)
  const [historyLoading, setHistoryLoading] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const activeFileIdRef = useRef<number | null>(null)
  const skipHistoryLoadRef = useRef(false)
  const turnsRef = useRef<ChatTurn[]>([])

  useEffect(() => {
    activeFileIdRef.current = activeFileId
  }, [activeFileId])

  useEffect(() => {
    let cancelled = false
    void listFiles()
      .then((items) => {
        if (cancelled) return
        setFiles(items)
        if (items.length > 0) {
          setActiveFileId((prev) => prev ?? items[0].id)
        }
      })
      .catch(() => {
        if (!cancelled) {
          setUploadError(t('fileQa.loadFailed'))
        }
      })
      .finally(() => {
        if (!cancelled) {
          setFilesLoading(false)
        }
      })
    return () => {
      cancelled = true
    }
  }, [t])

  const activeFile = files.find((f) => f.id === activeFileId) ?? null

  const { turns, isStreaming, sendMessage, stopGeneration, setTurns } = useFileStream({
    conversationId,
    fileId: activeFileId ? String(activeFileId) : null,
    onStreamComplete: () => {
      skipHistoryLoadRef.current = true
    },
  })
  turnsRef.current = turns

  const loadFileHistory = useCallback(
    async (fileId: number) => {
      setHistoryLoading(true)
      try {
        const detail = await getFileSessionDetail(String(fileId))
        if (activeFileIdRef.current !== fileId) return

        const convId = resolveFileConversationId(fileId, detail.conversationId)
        setConversationId(convId)
        setTurns(mergeSessionTurns(turnsRef.current, sessionMessagesToTurns(detail.messages)))
      } catch {
        if (activeFileIdRef.current !== fileId) return
        const convId = resolveFileConversationId(fileId)
        setConversationId(convId)
        setTurns([])
      } finally {
        if (activeFileIdRef.current === fileId) {
          setHistoryLoading(false)
        }
      }
    },
    [setTurns],
  )

  useEffect(() => {
    if (activeFileId == null || isStreaming) return
    if (skipHistoryLoadRef.current) {
      skipHistoryLoadRef.current = false
      return
    }
    void loadFileHistory(activeFileId)
  }, [activeFileId, isStreaming, loadFileHistory])

  const handleSelectFile = useCallback(
    (fileId: number) => {
      if (isStreaming || fileId === activeFileId) return
      setActiveFileId(fileId)
    },
    [activeFileId, isStreaming],
  )

  const handleNewChat = useCallback(() => {
    navigate(`/chat/${createConversationId()}`)
  }, [navigate])

  const filteredFiles = useMemo(() => {
    const q = searchQuery.trim().toLowerCase()
    if (!q) return files
    return files.filter((f) => f.name.toLowerCase().includes(q))
  }, [files, searchQuery])

  const handleUpload = useCallback(
    async (fileList: FileList | null) => {
      const file = fileList?.[0]
      if (!file) return

      setUploading(true)
      setUploadError(null)
      try {
        const uploaded = await uploadFile(file)
        setFiles((prev) => [...prev, uploaded])
        const newConversationId = createFileConversationId(uploaded.id)
        setConversationId(newConversationId)
        setTurns([])
        setActiveFileId(uploaded.id)
      } catch (err) {
        setUploadError(err instanceof Error ? err.message : t('fileQa.uploadFailed'))
      } finally {
        setUploading(false)
        if (fileInputRef.current) {
          fileInputRef.current.value = ''
        }
      }
    },
    [setTurns, t],
  )

  const handleDeleteFile = useCallback(
    async (id: number) => {
      try {
        await deleteFile(id)
        setFiles((prev) => prev.filter((f) => f.id !== id))
        if (activeFileId === id) {
          const remaining = files.filter((f) => f.id !== id)
          setActiveFileId(remaining[0]?.id ?? null)
        }
      } catch {
        setUploadError(t('fileQa.deleteFailed'))
      }
    },
    [activeFileId, files, t],
  )

  const handleSend = useCallback(
    (message: string) => {
      if (!activeFileId || !isFileReady(activeFile?.status)) return
      storeFileConversationId(activeFileId, conversationId)
      void sendMessage(message, t)
    },
    [activeFile?.status, activeFileId, conversationId, sendMessage, t],
  )

  const handleRecommendSelect = useCallback(
    (question: string) => {
      handleSend(question)
    },
    [handleSend],
  )

  const sendDisabled =
    !activeFileId || !isFileReady(activeFile?.status) || isStreaming || uploading || historyLoading

  const welcomeState = (
    <div className="flex gap-3">
      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-primary to-secondary">
        <Icon name="smart_toy" className="text-on-primary text-lg" />
      </div>
      <div className="glass-panel max-w-[85%] rounded-2xl rounded-tl-none border-primary/10 p-4">
        <p className="text-sm leading-relaxed text-on-surface">
          {activeFile
            ? t('fileQa.welcomeWithFile', { name: activeFile.name })
            : t('fileQa.welcomeEmpty')}
        </p>
      </div>
    </div>
  )

  return (
    <AppShell
      onNewChat={handleNewChat}
      searchQuery={searchQuery}
      onSearchChange={setSearchQuery}
      onSearchSubmit={() => undefined}
    >
      <div className="flex min-h-0 flex-1 overflow-hidden pb-16 md:pb-0">
        <aside className="hidden h-full w-[320px] shrink-0 flex-col overflow-hidden border-r border-outline-variant/10 bg-surface-container-low xl:flex">
          <div className="flex h-header-height shrink-0 items-center border-b border-outline-variant/10 p-container-padding">
            <h3 className="flex items-center gap-2 font-headline-md text-headline-md text-on-surface">
              <Icon name="folder_open" className="text-primary" />
              {t('fileQa.knowledgeBase')}
            </h3>
          </div>

          <div className="min-h-0 flex-1 space-y-4 overflow-hidden p-4">
            <div className="space-y-2">
              <p className="section-caption">{t('fileQa.activeFiles')}</p>
              {filesLoading && (
                <p className="px-2 text-[12px] text-on-surface-variant">{t('fileQa.loading')}</p>
              )}
              {!filesLoading && filteredFiles.length === 0 && (
                <p className="px-2 text-[12px] text-on-surface-variant">{t('fileQa.noFiles')}</p>
              )}
              {filteredFiles.map((file) => {
                  const { icon, iconBg, iconColor } = getFileIcon(file.fileType, file.name)
                  const isActive = file.id === activeFileId
                  return (
                    <div
                      key={file.id}
                      role="button"
                      tabIndex={0}
                      onClick={() => handleSelectFile(file.id)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          handleSelectFile(file.id)
                        }
                      }}
                      className={`glass-panel group flex cursor-pointer items-start gap-3 rounded-xl p-3 transition-colors ${
                        isActive
                          ? 'border-primary/20 bg-primary/5 hover:bg-primary/10'
                          : 'border-outline-variant/10 hover:bg-surface-container-high'
                      }`}
                    >
                      <div
                        className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-lg ${iconBg}`}
                      >
                        <Icon name={icon} className={iconColor} filled />
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="truncate font-label-md text-sm font-medium leading-snug text-on-surface">
                          {file.name}
                        </p>
                        <p className="text-[12px] text-on-surface-variant">
                          {formatFileSize(file.size)} • {fileStatusLabel(file.status, t, file.fileType, file.name)}
                        </p>
                      </div>
                      <button
                        type="button"
                        title={t('fileQa.deleteFile')}
                        onClick={(e) => {
                          e.stopPropagation()
                          void handleDeleteFile(file.id)
                        }}
                        className="shrink-0 text-on-surface-variant opacity-0 transition-all hover:text-error group-hover:opacity-100"
                      >
                        <Icon name="close" className="text-[18px]" />
                      </button>
                    </div>
                  )
                })}
            </div>

            <label className="group flex cursor-pointer flex-col items-center justify-center gap-3 rounded-2xl border-2 border-dashed border-outline-variant/20 bg-surface-container-low/50 p-6 text-center transition-all hover:border-primary/50">
              <input
                ref={fileInputRef}
                type="file"
                className="hidden"
                accept=".pdf,.doc,.docx,.txt,.md,.png,.jpg,.jpeg,.webp"
                disabled={uploading}
                onChange={(e) => void handleUpload(e.target.files)}
              />
              <div className="flex h-12 w-12 items-center justify-center rounded-full bg-surface-container-highest text-on-surface-variant transition-all group-hover:scale-110 group-hover:text-primary">
                <Icon name={uploading ? 'progress_activity' : 'upload_file'} spin={uploading} />
              </div>
              <div>
                <p className="font-label-md text-on-surface">{t('fileQa.uploadTitle')}</p>
                <p className="text-[11px] text-on-surface-variant">{t('fileQa.uploadHint')}</p>
              </div>
            </label>
            {uploadError && (
              <p className="truncate text-center text-[11px] text-error">{uploadError}</p>
            )}
          </div>
        </aside>

        <section className="relative flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden bg-surface-container-lowest">
          {historyLoading ? (
            <div className="flex min-h-0 flex-1 items-center justify-center">
              <Icon name="progress_activity" className="text-3xl text-primary" spin />
            </div>
          ) : (
            <MessageList
              turns={turns}
              isStreaming={isStreaming}
              onRecommendSelect={handleRecommendSelect}
              emptyState={welcomeState}
              maxWidthClass="max-w-[900px]"
              layout="embedded"
            />
          )}

          <ConversationInput
            placeholder={
              activeFile ? t('fileQa.inputPlaceholder') : t('fileQa.inputPlaceholderNoFile')
            }
            disclaimer={t('fileQa.poweredBy')}
            sendTitle={t('input.send')}
            attachTitle={t('input.attach')}
            onSend={handleSend}
            disabled={isStreaming}
            sendDisabled={sendDisabled}
            attachDisabled={uploading || isStreaming || historyLoading}
            isStreaming={isStreaming}
            onStop={() => void stopGeneration()}
            layout="embedded"
            maxWidthClass="max-w-[900px]"
            attachable
            onAttachClick={() => fileInputRef.current?.click()}
          />
        </section>
      </div>
    </AppShell>
  )
}
