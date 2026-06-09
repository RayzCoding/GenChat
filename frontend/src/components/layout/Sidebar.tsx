import { useTranslation } from 'react-i18next'
import { Link, useLocation } from 'react-router-dom'
import { Icon } from '../ui/Icon'

const navItems = [
  { key: 'aiChat', icon: 'chat_bubble', href: '/chat', match: /^\/chat/ },
  { key: 'fileQa', icon: 'description', href: '/file-qa', match: /^\/file-qa/ },
  { key: 'pptGen', icon: 'present_to_all', href: '/ppt', match: /^\/ppt/ },
  { key: 'deepResearch', icon: 'biotech', href: '/deep-research', match: /^\/deep-research/ },
  { key: 'skills', icon: 'extension', href: '/skills', match: /^\/skills/ },
] as const

interface SidebarProps {
  onNewChat: () => void
}

export function Sidebar({ onNewChat }: SidebarProps) {
  const { t } = useTranslation()
  const location = useLocation()

  return (
    <aside className="fixed left-0 top-0 z-50 hidden h-full w-sidebar-width flex-col gap-element-gap border-r border-outline-variant/10 bg-surface-container-lowest/70 p-container-padding shadow-2xl backdrop-blur-3xl md:flex">
      <div className="mb-8">
        <h1 className="font-headline-md text-headline-md font-bold tracking-tighter text-primary">
          {t('app.name')}
        </h1>
        <p className="font-label-md text-on-surface-variant opacity-70">{t('app.proAccount')}</p>
      </div>

      <button
        type="button"
        onClick={onNewChat}
        className="flex w-full items-center justify-center gap-2 rounded-xl bg-primary-container py-4 font-headline-md text-headline-md text-on-primary-container shadow-lg shadow-primary-container/20 transition-all hover:opacity-90 active:scale-95"
      >
        <Icon name="add" />
        {t('nav.newChat')}
      </button>

      <nav className="mt-6 flex-1 space-y-2">
        {navItems.map((item) => {
          const isActive = item.match.test(location.pathname)

          if (isActive) {
            return (
              <div
                key={item.key}
                className="flex cursor-pointer items-center gap-3 rounded-lg bg-secondary-container p-3 text-on-secondary-container shadow-lg"
              >
                <Icon name={item.icon} />
                <span className="font-body-md">{t(`nav.${item.key}`)}</span>
              </div>
            )
          }

          return (
            <Link
              key={item.key}
              to={item.href}
              className="flex items-center gap-3 rounded-lg p-3 text-on-surface-variant transition-all hover:bg-surface-container-highest/50 hover:text-on-surface"
            >
              <Icon name={item.icon} />
              <span className="font-body-md">{t(`nav.${item.key}`)}</span>
            </Link>
          )
        })}
      </nav>

      <div className="mt-auto space-y-2 border-t border-outline-variant/10 pt-4">
        <div className="flex cursor-pointer items-center gap-3 rounded-lg p-3 text-on-surface-variant transition-all hover:bg-surface-container-highest/50 hover:text-on-surface">
          <Icon name="settings" />
          <span className="font-body-md">{t('nav.settings')}</span>
        </div>
        <div className="flex cursor-pointer items-center gap-3 rounded-lg p-3 text-on-surface-variant transition-all hover:bg-surface-container-highest/50 hover:text-on-surface">
          <Icon name="help" />
          <span className="font-body-md">{t('nav.support')}</span>
        </div>
      </div>
    </aside>
  )
}
