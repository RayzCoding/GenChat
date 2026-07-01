import { getPresignedFileUrl } from '../services/fileApi'

export async function fetchPptFileBuffer(fileUrl: string): Promise<ArrayBuffer> {
  const downloadUrl = await getPresignedFileUrl(fileUrl)
  const response = await fetch(downloadUrl)
  if (!response.ok) {
    throw new Error(`Failed to fetch PPT (${response.status})`)
  }
  return response.arrayBuffer()
}

export async function openPptDownload(fileUrl: string): Promise<void> {
  const downloadUrl = await getPresignedFileUrl(fileUrl)
  window.open(downloadUrl, '_blank', 'noopener,noreferrer')
}
