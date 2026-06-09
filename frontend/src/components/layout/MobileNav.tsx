import { Link, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Icon } from '../ui/Icon'

const items = [
  { icon: 'chat', labelKey: 'mobile.chat', href: '/chat', match: /^\/chat/ },
  { icon: 'folder_open', labelKey: 'mobile.files', href: '/file-qa', match: /^\/file-qa/ },
  { icon: 'auto_awesome', labelKey: 'mobile.create', href: '/ppt', match: /^\/ppt/ },
  { icon: 'travel_explore', labelKey: 'mobile.research', href: '/deep-research', match: /^\/deep-research/ },
  { icon: 'extension', labelKey: 'mobile.skills', href: '/skills', match: /^\/skills/ },
] as const

export function MobileNav() {
  const { t } = useTranslation()
  const location = useLocation()

  return (
    <nav className="fixed bottom-0 left-0 z-50 flex h-16 w-full items-center justify-around rounded-t-xl border-t border-outline-variant/20 bg-surface-container-high/80 shadow-[0_-10px_30px_rgba(0,0,0,0.5)] backdrop-blur-2xl md:hidden">
      {items.map((item) => {
        const isActive = item.match.test(location.pathname)
        return (
          <Link
            key={item.labelKey}
            to={item.href}
            className={`flex flex-col items-center justify-center rounded-xl px-4 py-1 transition-all ${
              isActive
                ? 'bg-primary-container text-on-primary-container'
                : 'text-on-surface-variant hover:bg-surface-variant/30'
            }`}
          >
            <Icon name={item.icon} filled={isActive} />
            <span className="font-label-md text-label-md">{t(item.labelKey)}</span>
          </Link>
        )
      })}
    </nav>
  )
}
