import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import zhCN from './locales/zh-CN.json'
import enUS from './locales/en-US.json'

const STORAGE_KEY = 'genchat-lang'

function detectLanguage(): string {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored === 'zh-CN' || stored === 'en-US') {
    return stored
  }
  return navigator.language.startsWith('zh') ? 'zh-CN' : 'en-US'
}

i18n.use(initReactI18next).init({
  resources: {
    'zh-CN': { translation: zhCN },
    'en-US': { translation: enUS },
  },
  lng: detectLanguage(),
  fallbackLng: 'zh-CN',
  interpolation: { escapeValue: false },
})

export function setLanguage(lang: 'zh-CN' | 'en-US') {
  localStorage.setItem(STORAGE_KEY, lang)
  void i18n.changeLanguage(lang)
}

export default i18n
