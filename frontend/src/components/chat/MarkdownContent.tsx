import type { MouseEvent } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import type { Components } from 'react-markdown'

interface MarkdownContentProps {
  content: string
  isStreaming?: boolean
  onLinkClick?: (href: string, event: MouseEvent<HTMLAnchorElement>) => void
}

const baseComponents: Components = {
  h3: ({ children }) => (
    <div className="rounded-xl border border-outline-variant/10 bg-surface-container-high/50 p-4">
      <strong className="mb-1 block text-primary">{children}</strong>
    </div>
  ),
  strong: ({ children }) => <strong className="text-on-surface">{children}</strong>,
}

export function MarkdownContent({ content, isStreaming, onLinkClick }: MarkdownContentProps) {
  if (!content && !isStreaming) return null

  const components: Components = onLinkClick
    ? {
        ...baseComponents,
        a: ({ href, children }) => (
          <a
            href={href}
            target="_blank"
            rel="noopener noreferrer"
            className="text-primary underline underline-offset-2 hover:opacity-80"
            onClick={(event) => {
              if (href) {
                onLinkClick(href, event)
              }
            }}
          >
            {children}
          </a>
        ),
      }
    : baseComponents

  return (
    <div className="markdown-body">
      {content ? (
        <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>
          {content}
        </ReactMarkdown>
      ) : isStreaming ? (
        <span className="inline-block h-4 w-4 animate-pulse rounded-full bg-primary/40" />
      ) : null}
    </div>
  )
}
