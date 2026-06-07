interface UserBubbleProps {
  content: string
}

export function UserBubble({ content }: UserBubbleProps) {
  return (
    <div className="flex justify-end duration-500 animate-in fade-in slide-in-from-right-4">
      <div className="cyber-border max-w-[80%] rounded-2xl rounded-tr-sm bg-secondary-container/90 px-6 py-4 shadow-xl">
        <p className="font-body-md text-on-secondary-container">{content}</p>
      </div>
    </div>
  )
}
