import { useTranslation } from 'react-i18next'
import { Icon } from '../ui/Icon'
import { setLanguage } from '../../i18n'

const navItems = [
  { key: 'aiChat', icon: 'chat_bubble', active: true, href: '/chat' },
  { key: 'fileQa', icon: 'description', active: false, href: '#' },
  { key: 'pptGen', icon: 'present_to_all', active: false, href: '#' },
  { key: 'deepResearch', icon: 'biotech', active: false, href: '#' },
] as const

interface SidebarProps {
  onNewChat: () => void
}

export function Sidebar({ onNewChat }: SidebarProps) {
  const { t, i18n } = useTranslation()

  return (
    <aside className="fixed left-0 top-0 z-50 hidden h-full w-sidebar-width flex-col gap-element-gap border-r border-outline-variant/10 bg-surface-container-lowest/70 p-container-padding shadow-2xl backdrop-blur-3xl md:flex">
      <div className="mb-4">
        <h1 className="font-display-lg text-display-lg font-bold text-primary">{t('app.name')}</h1>
        <p className="font-label-md text-on-surface-variant">{t('app.proAccount')}</p>
      </div>

      <button
        type="button"
        onClick={onNewChat}
        className="flex w-full items-center justify-center gap-2 rounded-xl bg-primary-container py-3 font-label-md text-on-primary-container shadow-lg shadow-primary-container/20 transition-all active:scale-95"
      >
        <Icon name="chat_bubble" />
        {t('nav.newChat')}
      </button>

      <nav className="flex-1 space-y-1">
        {navItems.map((item) =>
          item.active ? (
            <div
              key={item.key}
              className="flex cursor-pointer items-center gap-3 rounded-lg bg-secondary-container p-3 text-on-secondary-container"
            >
              <Icon name={item.icon} />
              <span className="font-body-md">{t(`nav.${item.key}`)}</span>
            </div>
          ) : (
            <a
              key={item.key}
              href={item.href}
              className="flex cursor-pointer items-center gap-3 rounded-lg p-3 text-on-surface-variant transition-all hover:bg-surface-container-highest/50 hover:text-on-surface"
            >
              <Icon name={item.icon} />
              <span className="font-body-md">{t(`nav.${item.key}`)}</span>
            </a>
          ),
        )}
      </nav>

      <div className="space-y-1 border-t border-outline-variant/10 pt-4">
        <div className="flex cursor-pointer items-center gap-3 rounded-lg p-3 text-on-surface-variant transition-all hover:bg-surface-container-highest/50 hover:text-on-surface">
          <Icon name="settings" />
          <span className="font-body-md">{t('nav.settings')}</span>
        </div>
        <div className="flex cursor-pointer items-center gap-3 rounded-lg p-3 text-on-surface-variant transition-all hover:bg-surface-container-highest/50 hover:text-on-surface">
          <Icon name="help" />
          <span className="font-body-md">{t('nav.support')}</span>
        </div>
      </div>

      <div className="mt-4 flex items-center gap-3 rounded-xl bg-surface-container/50 p-2">
        <div className="h-10 w-10 shrink-0 overflow-hidden rounded-lg">
          <img
            alt=""
            className="h-full w-full object-cover"
            src="/assets/user-avatar.jpg"
          />
        </div>
        <div className="min-w-0 overflow-hidden">
          <p className="truncate font-label-md text-on-surface">{t('user.name')}</p>
          <p className="truncate text-xs text-on-surface-variant">{t('user.role')}</p>
        </div>
      </div>

      <div className="mt-2 flex gap-1 rounded-lg bg-surface-container/50 p-1">
        {(['zh-CN', 'en-US'] as const).map((lang) => (
          <button
            key={lang}
            type="button"
            onClick={() => setLanguage(lang)}
            className={`flex-1 rounded-md py-1 text-xs transition ${
              i18n.language === lang
                ? 'bg-primary/20 text-primary'
                : 'text-on-surface-variant hover:text-on-surface'
            }`}
          >
            {lang === 'zh-CN' ? t('lang.zh') : t('lang.en')}
          </button>
        ))}
      </div>
    </aside>
  )
}
