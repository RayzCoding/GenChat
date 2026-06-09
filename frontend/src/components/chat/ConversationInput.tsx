import { useCallback, useRef, useState, type ChangeEvent, type KeyboardEvent } from 'react'
import { uploadFile } from '../../services/fileApi'
import { Icon } from '../ui/Icon'
import { StopButton } from './StopButton'
import { SuggestionChips, type SuggestionConfig } from './SuggestionChips'

export type ConversationInputLayout = 'fixed' | 'embedded'

interface ConversationInputProps {
  placeholder: string
  disclaimer?: string
  enterHint?: string
  sendTitle?: string
  attachTitle?: string
  onSend: (message: string, extras?: { fileId?: string }) => void
  disabled?: boolean
  isStreaming?: boolean
  onStop?: () => void
  layout?: ConversationInputLayout
  maxWidthClass?: string
  attachable?: boolean
  onAttachClick?: () => void
  suggestions?: SuggestionConfig[]
  showSuggestions?: boolean
}

export function ConversationInput({
  placeholder,
  disclaimer,
  enterHint,
  sendTitle = 'Send',
  attachTitle = 'Attach',
  onSend,
  disabled,
  isStreaming,
  onStop,
  layout = 'fixed',
  maxWidthClass = 'max-w-chat',
  attachable,
  onAttachClick,
  suggestions,
  showSuggestions,
}: ConversationInputProps) {
  const [value, setValue] = useState('')
  const [attachedFileId, setAttachedFileId] = useState<string | null>(null)
  const [attachedFileName, setAttachedFileName] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleSend = useCallback(() => {
    const trimmed = value.trim()
    if (!trimmed || disabled) return
    onSend(trimmed, attachedFileId ? { fileId: attachedFileId } : undefined)
    setValue('')
    setAttachedFileId(null)
    setAttachedFileName(null)
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto'
    }
  }, [value, disabled, onSend, attachedFileId])

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

  const handleFileChange = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    setUploading(true)
    try {
      const uploaded = await uploadFile(file)
      setAttachedFileId(String(uploaded.id))
      setAttachedFileName(uploaded.name)
    } catch {
      setAttachedFileId(null)
      setAttachedFileName(null)
    } finally {
      setUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  const shellClass =
    layout === 'fixed'
      ? 'fixed bottom-0 left-0 right-0 bg-gradient-to-t from-surface via-surface/90 to-transparent p-container-padding pb-20 md:bottom-0 md:left-sidebar-width md:pb-6'
      : 'shrink-0 border-t border-outline-variant/10 bg-surface/90 p-container-padding pb-20 backdrop-blur-xl md:pb-4'

  return (
    <div className={shellClass}>
      <div className={`relative mx-auto ${maxWidthClass}`}>
        {isStreaming && onStop && <StopButton onStop={onStop} />}

        {showSuggestions && suggestions && suggestions.length > 0 && (
          <SuggestionChips
            items={suggestions}
            onSelect={(prompt) => onSend(prompt)}
            disabled={disabled || isStreaming}
          />
        )}

        {attachedFileName && (
          <div className="mb-2 flex items-center gap-2 rounded-lg border border-primary/20 bg-primary-container/10 px-3 py-1.5 font-label-md text-on-primary-container">
            <Icon name="attach_file" className="text-sm" />
            <span className="truncate">{attachedFileName}</span>
            <button
              type="button"
              onClick={() => {
                setAttachedFileId(null)
                setAttachedFileName(null)
              }}
              className="ml-auto text-on-surface-variant hover:text-on-surface"
            >
              <Icon name="close" className="text-sm" />
            </button>
          </div>
        )}

        <div className="glass-panel ai-glow cyber-border flex items-end gap-3 rounded-3xl p-4 transition-all focus-within:border-primary/50">
          {attachable && (
            <>
              {!onAttachClick && (
                <input
                  ref={fileInputRef}
                  type="file"
                  className="hidden"
                  onChange={(e) => void handleFileChange(e)}
                />
              )}
              <button
                type="button"
                onClick={() => (onAttachClick ? onAttachClick() : fileInputRef.current?.click())}
                disabled={disabled || uploading}
                title={attachTitle}
                className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl text-on-surface-variant transition-colors hover:bg-surface-variant/30 hover:text-on-surface disabled:opacity-40"
              >
                <Icon name="attach_file" />
              </button>
            </>
          )}

          <div className="group/input min-h-[56px] flex-1 rounded-2xl border border-outline-variant/10 bg-surface-container-low/50 px-4 py-3 transition-all focus-within:bg-surface-container-high/50">
            <textarea
              ref={textareaRef}
              value={value}
              onChange={handleInput}
              onKeyDown={handleKeyDown}
              disabled={disabled}
              rows={1}
              placeholder={placeholder}
              className="max-h-[200px] min-h-[24px] w-full resize-none border-none bg-transparent font-body-md text-on-surface outline-none placeholder:text-on-surface-variant focus:ring-0 disabled:opacity-60"
            />
            {enterHint && (
              <div className="mt-2 flex justify-end opacity-0 transition-opacity duration-200 group-focus-within/input:opacity-100">
                <span className="font-mono-code text-[10px] text-on-surface-variant/40">{enterHint}</span>
              </div>
            )}
          </div>

          <button
            type="button"
            onClick={handleSend}
            disabled={disabled || !value.trim()}
            title={sendTitle}
            className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-primary text-on-primary shadow-lg shadow-primary/20 transition-all hover:opacity-90 active:scale-90 disabled:opacity-40"
          >
            <Icon name="arrow_upward" filled />
          </button>
        </div>

        {disclaimer && (
          <p className="mt-3 text-center text-[11px] text-on-surface-variant opacity-50">{disclaimer}</p>
        )}
      </div>
    </div>
  )
}
