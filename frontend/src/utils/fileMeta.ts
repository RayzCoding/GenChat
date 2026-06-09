export function formatFileSize(bytes?: number): string {
  if (bytes === undefined || bytes === null) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function getFileIcon(fileType?: string, name?: string): {
  icon: string
  iconBg: string
  iconColor: string
} {
  const ext = (fileType || name?.split('.').pop() || '').toLowerCase()
  if (ext.includes('pdf')) {
    return { icon: 'picture_as_pdf', iconBg: 'bg-error-container/20', iconColor: 'text-error' }
  }
  if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'image'].some((t) => ext.includes(t))) {
    return { icon: 'image', iconBg: 'bg-tertiary-container/20', iconColor: 'text-tertiary' }
  }
  return { icon: 'description', iconBg: 'bg-primary-container/20', iconColor: 'text-primary' }
}

export function fileStatusLabel(
  status: string,
  t: (key: string) => string,
  fileType?: string,
  name?: string,
): string {
  const normalized = status?.toLowerCase()
  const ext = (fileType || name?.split('.').pop() || '').toLowerCase()
  const isImage = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'image'].some((type) =>
    ext.includes(type),
  )

  if (normalized === 'success' && isImage) {
    return t('fileQa.status.ocrActive')
  }

  switch (normalized) {
    case 'success':
      return t('fileQa.status.ready')
    case 'processing':
    case 'pending':
      return t('fileQa.status.processing')
    case 'failed':
      return t('fileQa.status.failed')
    default:
      return status
  }
}
