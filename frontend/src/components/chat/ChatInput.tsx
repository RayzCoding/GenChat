import { useTranslation } from 'react-i18next'
import { ConversationInput } from './ConversationInput'

interface ChatInputProps {
  onSend: (message: string) => void
  disabled?: boolean
  isStreaming?: boolean
  onStop?: () => void
}

export function ChatInput({ onSend, disabled, isStreaming, onStop }: ChatInputProps) {
  const { t } = useTranslation()

  return (
    <ConversationInput
      placeholder={t('chat.inputPlaceholder')}
      enterHint={t('input.enterHint')}
      disclaimer={t('input.disclaimer')}
      sendTitle={t('input.send')}
      onSend={onSend}
      disabled={disabled}
      isStreaming={isStreaming}
      onStop={onStop}
      layout="fixed"
    />
  )
}
