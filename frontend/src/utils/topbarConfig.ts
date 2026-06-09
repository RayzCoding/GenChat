export type TopBarLayout = 'sticky' | 'fixed'

export interface TopBarRouteConfig {
  titleKey: string
  searchKey: string
  layout: TopBarLayout
  titleClassName: string
  enableSessionSearch: boolean
}

/** Shared page title style (File Q&A Assistant baseline) */
export const TOPBAR_PAGE_TITLE_CLASS =
  'font-headline-md text-headline-md font-bold text-on-surface'

export function getTopBarConfig(pathname: string): TopBarRouteConfig {
  if (pathname.startsWith('/history')) {
    return {
      titleKey: 'topbar.pageTitleHistory',
      searchKey: 'topbar.searchPlaceholder',
      layout: 'fixed',
      titleClassName: TOPBAR_PAGE_TITLE_CLASS,
      enableSessionSearch: true,
    }
  }
  if (pathname.startsWith('/file-qa')) {
    return {
      titleKey: 'fileQa.pageTitle',
      searchKey: 'fileQa.searchPlaceholder',
      layout: 'sticky',
      titleClassName: TOPBAR_PAGE_TITLE_CLASS,
      enableSessionSearch: false,
    }
  }
  if (pathname.startsWith('/ppt')) {
    return {
      titleKey: 'ppt.pageTitle',
      searchKey: 'ppt.searchPlaceholder',
      layout: 'sticky',
      titleClassName: TOPBAR_PAGE_TITLE_CLASS,
      enableSessionSearch: false,
    }
  }
  if (pathname.startsWith('/deep-research')) {
    return {
      titleKey: 'deepResearch.pageTitle',
      searchKey: 'deepResearch.searchPlaceholder',
      layout: 'fixed',
      titleClassName: TOPBAR_PAGE_TITLE_CLASS,
      enableSessionSearch: false,
    }
  }
  if (pathname.startsWith('/skills')) {
    return {
      titleKey: 'skills.pageTitle',
      searchKey: 'skills.searchPlaceholder',
      layout: 'sticky',
      titleClassName: TOPBAR_PAGE_TITLE_CLASS,
      enableSessionSearch: true,
    }
  }
  return {
    titleKey: 'topbar.pageTitleChat',
    searchKey: 'topbar.searchPlaceholder',
    layout: 'sticky',
    titleClassName: TOPBAR_PAGE_TITLE_CLASS,
    enableSessionSearch: true,
  }
}

export function needsFixedTopBarOffset(pathname: string): boolean {
  return getTopBarConfig(pathname).layout === 'fixed'
}
