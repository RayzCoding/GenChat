import { Icon } from '../ui/Icon'

export function MobileNav() {
  const items = [
    { icon: 'chat', label: 'Chat', active: true },
    { icon: 'folder_open', label: 'Files', active: false },
    { icon: 'auto_awesome', label: 'Create', active: false },
    { icon: 'travel_explore', label: 'Research', active: false },
  ]

  return (
    <nav className="fixed bottom-0 left-0 z-50 flex h-16 w-full items-center justify-around rounded-t-xl border-t border-outline-variant/20 bg-surface-container-high/80 shadow-[0_-10px_30px_rgba(0,0,0,0.5)] backdrop-blur-2xl md:hidden">
      {items.map((item) => (
        <div
          key={item.label}
          className={`flex flex-col items-center justify-center rounded-xl px-4 py-1 ${
            item.active
              ? 'bg-primary-container text-on-primary-container'
              : 'text-on-surface-variant transition-all hover:bg-surface-variant/30'
          }`}
        >
          <Icon name={item.icon} />
          <span className="font-label-md text-label-md">{item.label}</span>
        </div>
      ))}
    </nav>
  )
}
