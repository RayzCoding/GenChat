interface PptUserBubbleProps {
  content: string
}

export function PptUserBubble({ content }: PptUserBubbleProps) {
  return (
    <div className="flex flex-col items-end gap-2 duration-500 animate-in fade-in slide-in-from-right-4">
      <div className="chat-bubble-user max-w-[80%] rounded-2xl border border-primary/20 bg-primary-container/20 px-6 py-3 text-on-primary-container">
        <p className="font-body-md">{content}</p>
      </div>
    </div>
  )
}
