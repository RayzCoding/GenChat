import { useCallback, useRef, useState, type ChangeEvent, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Icon } from '../ui/Icon'
import { StopButton } from './StopButton'

interface ChatInputProps {
  onSend: (message: string) => void
  disabled?: boolean
  isStreaming?: boolean
  onStop?: () => void
}

export function ChatInput({ onSend, disabled, isStreaming, onStop }: ChatInputProps) {
  const { t } = useTranslation()
  const [value, setValue] = useState('')
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  const handleSend = useCallback(() => {
    const trimmed = value.trim()
    if (!trimmed || disabled) return
    onSend(trimmed)
    setValue('')
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto'
    }
  }, [value, disabled, onSend])

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  const handleInput = (e: ChangeEvent<HTMLTextAreaElement>) => {
    setValue(e.target.value)
    const el = e.target
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, 200)}px`
  }

  return (
    <div className="fixed bottom-0 left-0 right-0 bg-gradient-to-t from-surface via-surface/90 to-transparent p-container-padding pb-20 md:bottom-0 md:left-sidebar-width md:pb-6">
      <div className="group relative mx-auto max-w-chat">
        {isStreaming && onStop && <StopButton onStop={onStop} />}

        <div className="glass-panel ai-glow cyber-border flex items-end gap-3 rounded-3xl p-4 transition-all group-focus-within:border-primary/50">
          <div className="min-h-[56px] flex-1 rounded-2xl border border-outline-variant/10 bg-surface-container-low/50 px-4 py-3 transition-all group-focus-within:bg-surface-container-high/50">
            <textarea
              ref={textareaRef}
              value={value}
              onChange={handleInput}
              onKeyDown={handleKeyDown}
              disabled={disabled}
              rows={1}
              placeholder={t('chat.inputPlaceholder')}
              className="max-h-[200px] min-h-[24px] w-full resize-none border-none bg-transparent font-body-md text-on-surface placeholder:text-on-surface-variant focus:ring-0 disabled:opacity-60"
            />
            <div className="ml-auto mt-2 flex items-center gap-2">
              <span className="font-mono-code text-[10px] text-on-surface-variant/50">
                Enter to Send
              </span>
            </div>
          </div>
          <button
            type="button"
            onClick={handleSend}
            disabled={disabled || !value.trim()}
            title={t('chat.send')}
            className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-primary text-on-primary shadow-lg shadow-primary/20 transition-all hover:opacity-90 active:scale-90 disabled:opacity-40"
          >
            <Icon name="arrow_upward" filled />
          </button>
        </div>
        <p className="mt-3 text-center text-[11px] text-on-surface-variant opacity-50">
          {t('chat.disclaimer')}
        </p>
      </div>
    </div>
  )
}
