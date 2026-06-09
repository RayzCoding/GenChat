import { useTranslation } from 'react-i18next'
import { setLanguage } from '../../i18n'

export function LanguageSwitcher() {
  const { i18n, t } = useTranslation()
  const isZh = i18n.language.startsWith('zh')

  const switchTo = (lang: 'zh-CN' | 'en-US') => {
    if ((lang === 'zh-CN' && isZh) || (lang === 'en-US' && !isZh)) return
    setLanguage(lang)
  }

  return (
    <div
      className="flex items-center rounded-full border border-outline-variant/20 bg-surface-container p-0.5"
      role="group"
      aria-label={t('topbar.language')}
    >
      <button
        type="button"
        onClick={() => switchTo('zh-CN')}
        className={`rounded-full px-2.5 py-1 font-label-md text-[12px] transition-all ${
          isZh
            ? 'bg-primary-container text-on-primary-container shadow-sm'
            : 'text-on-surface-variant hover:text-on-surface'
        }`}
        aria-pressed={isZh}
      >
        {t('lang.zh')}
      </button>
      <button
        type="button"
        onClick={() => switchTo('en-US')}
        className={`rounded-full px-2.5 py-1 font-label-md text-[12px] transition-all ${
          !isZh
            ? 'bg-primary-container text-on-primary-container shadow-sm'
            : 'text-on-surface-variant hover:text-on-surface'
        }`}
        aria-pressed={!isZh}
      >
        {t('lang.en')}
      </button>
    </div>
  )
}
