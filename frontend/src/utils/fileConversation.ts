import { createConversationId } from './chat'

const STORAGE_PREFIX = 'genchat:file-qa:conversation:'

export function fileConversationStorageKey(fileId: number | string): string {
  return `${STORAGE_PREFIX}${fileId}`
}

export function readStoredFileConversationId(fileId: number | string): string | null {
  return localStorage.getItem(fileConversationStorageKey(fileId))
}

export function storeFileConversationId(fileId: number | string, conversationId: string): void {
  localStorage.setItem(fileConversationStorageKey(fileId), conversationId)
}

export function createFileConversationId(fileId: number | string): string {
  const conversationId = createConversationId()
  storeFileConversationId(fileId, conversationId)
  return conversationId
}

export function resolveFileConversationId(
  fileId: number | string,
  serverConversationId?: string | null,
): string {
  if (serverConversationId) {
    storeFileConversationId(fileId, serverConversationId)
    return serverConversationId
  }
  return readStoredFileConversationId(fileId) ?? createFileConversationId(fileId)
}
