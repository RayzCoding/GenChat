import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { init } from 'pptx-preview'
import { Icon } from '../ui/Icon'
import { fetchPptFileBuffer } from '../../utils/pptDownload'

interface PptSlideViewerProps {
  fileUrl: string
}

export function PptSlideViewer({ fileUrl }: PptSlideViewerProps) {
  const { t } = useTranslation()
  const containerRef = useRef<HTMLDivElement>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    let previewer: ReturnType<typeof init> | null = null
    let cancelled = false

    async function loadPreview() {
      const host = containerRef.current
      if (!host) return

      setLoading(true)
      setError(null)
      host.innerHTML = ''

      try {
        const buffer = await fetchPptFileBuffer(fileUrl)
        if (cancelled || !containerRef.current) return

        const width = host.clientWidth || 960
        previewer = init(host, {
          width,
          height: Math.round(width * 9 / 16),
          mode: 'slide',
        })
        await previewer.preview(buffer)
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : t('ppt.preview.loadFailed'))
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    void loadPreview()

    return () => {
      cancelled = true
      previewer?.destroy()
      if (containerRef.current) {
        containerRef.current.innerHTML = ''
      }
    }
  }, [fileUrl, t])

  return (
    <div className="relative w-full overflow-hidden rounded-xl border border-outline-variant/10 bg-surface-container-lowest">
      {loading && (
        <div className="flex aspect-video w-full items-center justify-center gap-2 font-label-md text-on-surface-variant">
          <Icon name="hourglass_empty" className="animate-spin text-primary" />
          {t('ppt.preview.loading')}
        </div>
      )}
      {error && !loading && (
        <div className="flex aspect-video w-full flex-col items-center justify-center gap-2 px-6 text-center font-label-md text-error">
          <Icon name="error_outline" className="text-3xl" />
          <p>{error}</p>
        </div>
      )}
      <div
        ref={containerRef}
        className={loading || error ? 'hidden' : 'min-h-[360px] w-full [&_.pptx-preview-wrapper]:mx-auto'}
      />
    </div>
  )
}
