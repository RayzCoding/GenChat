import { Suspense, lazy } from 'react'
import { useTranslation } from 'react-i18next'
import { Icon } from '../ui/Icon'
import { openPptDownload } from '../../utils/pptDownload'
import { PptInlineTitle } from './PptInlineTitle'

const PptSlideViewer = lazy(() =>
  import('./PptSlideViewer').then((module) => ({ default: module.PptSlideViewer })),
)

interface PptPreviewModalProps {
  open: boolean
  title: string
  slideCount: number | null
  fileUrl: string
  onClose: () => void
}

export function PptPreviewModal({ open, title, slideCount, fileUrl, onClose }: PptPreviewModalProps) {
  const { t } = useTranslation()

  if (!open) return null

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/60 p-container-padding backdrop-blur-sm"
      onClick={onClose}
      role="presentation"
    >
      <div
        className="relative max-h-[90vh] w-full max-w-5xl overflow-y-auto rounded-3xl border border-outline-variant/20 bg-surface-container-high p-6 shadow-2xl"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <button
          type="button"
          onClick={onClose}
          className="absolute right-4 top-4 z-10 rounded-full p-2 text-on-surface-variant transition-colors hover:bg-surface-variant hover:text-on-surface"
          aria-label={t('ppt.preview.close')}
        >
          <Icon name="close" />
        </button>

        <PptInlineTitle title={title} className="mb-2 pr-10 font-headline-md text-primary" />
        {slideCount != null && (
          <p className="mb-4 font-label-md text-on-surface-variant">
            {t('ppt.preview.slideCount', { count: slideCount })}
          </p>
        )}

        <Suspense
          fallback={
            <div className="flex aspect-video w-full items-center justify-center gap-2 rounded-xl border border-outline-variant/10 bg-surface-container-lowest font-label-md text-on-surface-variant">
              <Icon name="hourglass_empty" className="animate-spin text-primary" />
              {t('ppt.preview.loading')}
            </div>
          }
        >
          <PptSlideViewer fileUrl={fileUrl} />
        </Suspense>

        <p className="mt-4 font-body-md text-on-surface-variant">{t('ppt.preview.hint')}</p>

        <div className="mt-6 flex gap-4">
          <button
            type="button"
            onClick={onClose}
            className="flex-1 rounded-xl border border-outline-variant/30 py-3 font-label-md text-on-surface transition-colors hover:bg-surface-variant/30"
          >
            {t('ppt.preview.close')}
          </button>
          <button
            type="button"
            onClick={() => {
              void openPptDownload(fileUrl).catch(() => {
                window.open(fileUrl, '_blank', 'noopener,noreferrer')
              })
            }}
            className="flex flex-1 items-center justify-center gap-2 rounded-xl bg-primary py-3 font-label-md text-on-primary transition-all hover:opacity-90"
          >
            <Icon name="download" />
            {t('ppt.result.download')}
          </button>
        </div>
      </div>
    </div>
  )
}
