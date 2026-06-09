export interface SessionTypeMeta {
  labelKey: string
  icon: string
  badgeClass: string
  iconWrapClass: string
  titleHoverClass: string
  actionHoverClass: string
  actionIcon: string
  actionKey: string
}

const AGENT_META: Record<string, SessionTypeMeta> = {
  webSearchReactAgent: {
    labelKey: 'history.types.aiChat',
    icon: 'chat_bubble',
    badgeClass: 'text-secondary/80',
    iconWrapClass: 'bg-secondary-container/20 text-secondary',
    titleHoverClass: 'group-hover/card:text-secondary',
    actionHoverClass: 'hover:text-secondary',
    actionIcon: 'chat_paste_go',
    actionKey: 'history.continueChat',
  },
  fileReactAgent: {
    labelKey: 'history.types.fileQa',
    icon: 'description',
    badgeClass: 'text-tertiary/80',
    iconWrapClass: 'bg-tertiary-container/20 text-tertiary',
    titleHoverClass: 'group-hover/card:text-primary',
    actionHoverClass: 'hover:text-primary',
    actionIcon: 'file_open',
    actionKey: 'history.viewDetail',
  },
  deepResearchAgent: {
    labelKey: 'history.types.research',
    icon: 'biotech',
    badgeClass: 'text-primary/80',
    iconWrapClass: 'bg-primary-container/20 text-primary',
    titleHoverClass: 'group-hover/card:text-primary',
    actionHoverClass: 'hover:text-primary',
    actionIcon: 'arrow_forward',
    actionKey: 'history.viewDetail',
  },
  pptBuilderAgent: {
    labelKey: 'history.types.ppt',
    icon: 'present_to_all',
    badgeClass: 'text-primary-fixed/80',
    iconWrapClass: 'bg-primary-fixed-dim/20 text-primary-fixed',
    titleHoverClass: 'group-hover/card:text-primary',
    actionHoverClass: 'hover:text-primary',
    actionIcon: 'download',
    actionKey: 'history.exportPpt',
  },
  skillsReactAgent: {
    labelKey: 'history.types.skills',
    icon: 'extension',
    badgeClass: 'text-secondary/80',
    iconWrapClass: 'bg-secondary-container/20 text-secondary',
    titleHoverClass: 'group-hover/card:text-secondary',
    actionHoverClass: 'hover:text-secondary',
    actionIcon: 'arrow_forward',
    actionKey: 'history.continueChat',
  },
}

const DEFAULT_META = AGENT_META.webSearchReactAgent

export function getSessionTypeMeta(agentType?: string): SessionTypeMeta {
  if (agentType && AGENT_META[agentType]) {
    return AGENT_META[agentType]
  }
  return DEFAULT_META
}

export function truncateText(text: string | undefined, maxLength: number): string {
  if (!text) return ''
  if (text.length <= maxLength) return text
  return `${text.slice(0, maxLength)}...`
}

export function formatSessionDate(iso: string, locale: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat(locale.startsWith('zh') ? 'zh-CN' : 'en-US', {
    year: 'numeric',
    month: locale.startsWith('zh') ? 'long' : 'short',
    day: 'numeric',
  }).format(date)
}
