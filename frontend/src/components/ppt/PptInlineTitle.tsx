import { MarkdownContent } from '../chat/MarkdownContent'

interface PptInlineTitleProps {
  title: string
  className?: string
}

/** Render a short PPT title that may contain inline markdown from LLM summaries. */
export function PptInlineTitle({ title, className = '' }: PptInlineTitleProps) {
  return (
    <div className={`[&_.markdown-body>p]:m-0 [&_.markdown-body]:inline ${className}`}>
      <MarkdownContent content={title} />
    </div>
  )
}
