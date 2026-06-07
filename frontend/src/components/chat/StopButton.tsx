import { useTranslation } from 'react-i18next'
import { Icon } from '../ui/Icon'

interface StopButtonProps {
  onStop: () => void
}

export function StopButton({ onStop }: StopButtonProps) {
  const { t } = useTranslation()

  return (
    <div className="absolute -top-12 left-1/2 -translate-x-1/2 animate-bounce">
      <button
        type="button"
        onClick={onStop}
        className="flex items-center gap-2 rounded-full border border-error/20 bg-error-container/80 px-6 py-2 font-label-md text-on-error-container backdrop-blur-md transition-all hover:bg-error-container active:scale-90"
      >
        <Icon name="stop_circle" filled className="text-sm" />
        {t('chat.stopGenerating')}
      </button>
    </div>
  )
}
