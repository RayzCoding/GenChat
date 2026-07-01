import { useTranslation } from 'react-i18next'
import { Icon } from '../ui/Icon'
import { openPptDownload } from '../../utils/pptDownload'

interface PptResultCardProps {
  title: string
  slideCount: number | null
  theme?: string
  fileUrl: string
  onPreview: () => void
}

export function PptResultCard({ title, slideCount, theme, fileUrl, onPreview }: PptResultCardProps) {
  const { t } = useTranslation()

  const handleDownload = () => {
    void openPptDownload(fileUrl).catch(() => {
      window.open(fileUrl, '_blank', 'noopener,noreferrer')
    })
  }

  return (
    <div className="w-full max-w-2xl duration-700 animate-in fade-in slide-in-from-bottom-4">
      <div className="relative overflow-hidden rounded-3xl border border-outline-variant/20 bg-surface-container-highest/40 p-6 backdrop-blur-md">
        <div className="mb-4 flex items-center justify-between">
          <div>
            <h4 className="font-headline-md text-primary">{title}</h4>
            <p className="font-label-md text-on-surface-variant">
              {slideCount != null
                ? t('ppt.result.meta', { count: slideCount, theme: theme ?? t('ppt.result.defaultTheme') })
                : theme ?? t('ppt.result.defaultTheme')}
            </p>
          </div>
          <span className="rounded-full bg-primary/20 px-3 py-1 text-[10px] font-bold uppercase tracking-wider text-primary">
            {t('ppt.result.ready')}
          </span>
        </div>

        <button
          type="button"
          onClick={onPreview}
          className="group relative mb-6 aspect-video w-full overflow-hidden rounded-xl border border-outline-variant/10 bg-gradient-to-br from-primary/15 via-surface-container-low to-surface-container-lowest shadow-2xl"
        >
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 text-on-surface-variant">
            <span className="rounded-full bg-primary/15 p-4 text-primary transition-transform duration-500 group-hover:scale-110">
              <Icon name="slideshow" className="text-4xl" />
            </span>
            <span className="font-label-md opacity-80">{t('ppt.result.preview')}</span>
          </div>
          <div className="absolute inset-0 flex items-center justify-center bg-black/40 opacity-0 transition-opacity group-hover:opacity-100">
            <span className="rounded-full bg-white/10 p-4 text-white backdrop-blur-md">
              <Icon name="play_circle" className="text-3xl" />
            </span>
          </div>
        </button>

        <div className="flex gap-4">
          <button
            type="button"
            onClick={onPreview}
            className="flex flex-1 items-center justify-center gap-2 rounded-xl border border-outline-variant/30 bg-surface-container-highest py-3 font-label-md text-on-surface transition-colors hover:bg-surface-bright"
          >
            <Icon name="visibility" />
            {t('ppt.result.preview')}
          </button>
          <button
            type="button"
            onClick={handleDownload}
            className="flex flex-1 items-center justify-center gap-2 rounded-xl bg-primary py-3 font-label-md text-on-primary transition-all hover:shadow-[0_0_20px_rgba(178,197,255,0.4)] active:scale-95"
          >
            <Icon name="download" />
            {t('ppt.result.download')}
          </button>
        </div>
      </div>
    </div>
  )
}
