interface IconProps {
  name: string
  filled?: boolean
  className?: string
  spin?: boolean
}

export function Icon({ name, filled, className = '', spin }: IconProps) {
  return (
    <span
      className={`material-symbols-outlined ${filled ? 'material-symbols-filled' : ''} ${spin ? 'animate-spin' : ''} ${className}`}
      aria-hidden
    >
      {name}
    </span>
  )
}
