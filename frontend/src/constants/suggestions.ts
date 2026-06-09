import type { SuggestionConfig } from '../components/chat/SuggestionChips'

export const PPT_SUGGESTIONS: SuggestionConfig[] = [
  { icon: 'school', labelKey: 'ppt.suggestions.springBoot.label', promptKey: 'ppt.suggestions.springBoot.prompt' },
  { icon: 'trending_up', labelKey: 'ppt.suggestions.aiTrends.label', promptKey: 'ppt.suggestions.aiTrends.prompt' },
  { icon: 'rocket_launch', labelKey: 'ppt.suggestions.productLaunch.label', promptKey: 'ppt.suggestions.productLaunch.prompt' },
]

export const SKILLS_SUGGESTIONS: SuggestionConfig[] = [
  { icon: 'present_to_all', labelKey: 'skills.suggestions.pptLegal.label', promptKey: 'skills.suggestions.pptLegal.prompt' },
  { icon: 'analytics', labelKey: 'skills.suggestions.dataReport.label', promptKey: 'skills.suggestions.dataReport.prompt' },
  { icon: 'code', labelKey: 'skills.suggestions.codeReview.label', promptKey: 'skills.suggestions.codeReview.prompt' },
]
