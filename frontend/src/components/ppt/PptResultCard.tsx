import { useTranslation } from 'react-i18next'
import { Icon } from '../ui/Icon'

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
    window.open(fileUrl, '_blank', 'noopener,noreferrer')
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
          className="group relative mb-6 aspect-video w-full overflow-hidden rounded-xl border border-outline-variant/10 bg-surface-container-lowest shadow-2xl"
        >
          <img
            src="/assets/ppt-preview-cover.jpg"
            alt={title}
            className="h-full w-full object-cover opacity-90 transition-transform duration-500 group-hover:scale-105"
          />
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
