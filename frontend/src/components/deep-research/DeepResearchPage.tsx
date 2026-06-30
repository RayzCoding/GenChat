import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useDeepResearchStream } from '../../hooks/useDeepResearchStream'
import type { DeepResearchPlanTask } from '../../types/deepResearch'
import { createConversationId } from '../../utils/chat'
import { formatThinkingTimeline, terminalLineClass } from '../../utils/deepResearchUi'
import { AppShell } from '../layout/AppShell'
import { MarkdownContent } from '../chat/MarkdownContent'
import { Icon } from '../ui/Icon'
import { DeepResearchCitationList } from './DeepResearchCitationList'
import { ConversationInput } from '../chat/ConversationInput'

const PANEL_HEADER = 'mb-3 flex shrink-0 items-center gap-2 font-headline-md'
const PANEL_BODY = 'custom-scrollbar min-h-0 flex-1 overflow-y-auto'

function TaskRow({ task }: { task: DeepResearchPlanTask }) {
  const { t } = useTranslation()
  const isPending = task.status === 'pending'
  const isExecuting = task.status === 'executing'
  const isCompleted = task.status === 'completed'
  const isFailed = task.status === 'failed'

  return (
    <div
      className={`task-node relative flex items-start gap-4 ${isPending ? 'opacity-50' : isFailed ? 'opacity-70' : ''}`}
    >
      <div
        className={`z-10 flex h-8 w-8 shrink-0 items-center justify-center rounded-full ${
          isCompleted
            ? 'border border-tertiary/40 bg-tertiary/20 text-tertiary'
            : isExecuting
              ? 'border border-primary/40 bg-primary/20 text-primary'
              : isFailed
                ? 'border border-error/40 bg-error/20 text-error'
                : 'bg-surface-container-highest text-on-surface-variant'
        }`}
      >
        {isCompleted && <Icon name="check" className="text-[18px]" />}
        {isExecuting && <div className="h-2.5 w-2.5 animate-pulse rounded-full bg-primary" />}
        {isFailed && <Icon name="error" className="text-[18px]" />}
        {isPending && <Icon name="more_horiz" className="text-[18px]" />}
      </div>
      <div
        className={`flex-1 rounded-lg ${
          isExecuting
            ? 'glow-primary border border-primary/30 bg-surface-container-high p-4'
            : 'border border-outline-variant/10 bg-surface-container-low p-3'
        }`}
      >
        <div className={`flex items-center justify-between gap-3 ${isExecuting ? 'mb-2' : ''}`}>
          <p
            className={`font-body-md ${
              isExecuting ? 'font-semibold text-on-surface' : 'text-on-surface-variant'
            }`}
          >
            {task.instruction}
          </p>
          <span
            className={`shrink-0 rounded px-2 py-0.5 text-[10px] font-bold uppercase ${
              isCompleted
                ? 'bg-tertiary/10 text-tertiary'
                : isExecuting
                  ? 'bg-primary/10 text-primary'
                  : isFailed
                    ? 'bg-error/10 text-error'
                    : 'bg-surface-variant text-on-surface-variant'
            }`}
          >
            {t(`deepResearch.taskStatus.${task.status}`)}
          </span>
        </div>
        {isExecuting && (
          <div className="h-1 w-full overflow-hidden rounded-full bg-surface-variant">
            <div className="shimmer h-full bg-primary" style={{ width: '45%' }} />
          </div>
        )}
      </div>
    </div>
  )
}

export function DeepResearchPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [searchQuery, setSearchQuery] = useState('')
  const [conversationId] = useState(createConversationId)
  const terminalRef = useRef<HTMLDivElement>(null)

  const { state, isStreaming, error, sendQuestion, stopGeneration } =
    useDeepResearchStream(conversationId)

  const handleNewChat = useCallback(() => {
    navigate(`/chat/${createConversationId()}`)
  }, [navigate])

  const handleSend = useCallback(
    (question: string) => {
      void sendQuestion(question, t)
    },
    [sendQuestion, t],
  )

  const resumeMode = state.phase === 'awaiting_clarification'
  const hasStarted = state.phase !== 'idle'
  const terminalLines = formatThinkingTimeline(state.thinkingTimeline)

  const visibleTasks = useMemo(() => {
    const statusRank: Record<string, number> = {
      completed: 4,
      executing: 3,
      failed: 2,
      pending: 1,
    }
    const byInstruction = new Map<string, (typeof state.tasks)[number]>()
    for (const task of state.tasks) {
      const key = `${task.round}:${task.instruction}`
      const existing = byInstruction.get(key)
      if (
        !existing ||
        (statusRank[task.status] ?? 0) >= (statusRank[existing.status] ?? 0)
      ) {
        byInstruction.set(key, task)
      }
    }
    const deduped = Array.from(byInstruction.values())
    const q = searchQuery.trim().toLowerCase()
    if (!q) return deduped
    return deduped.filter((task) => task.instruction.toLowerCase().includes(q))
  }, [state.tasks, searchQuery])

  const filteredReferences = useMemo(() => {
    const q = searchQuery.trim().toLowerCase()
    if (!q) return state.references
    return state.references.filter(
      (r) =>
        (r.title ?? '').toLowerCase().includes(q) ||
        r.url.toLowerCase().includes(q) ||
        (r.content ?? '').toLowerCase().includes(q),
    )
  }, [state.references, searchQuery])

  useEffect(() => {
    const el = terminalRef.current
    if (el) el.scrollTop = el.scrollHeight
  }, [terminalLines.length, state.thinkingTimeline])

  return (
    <AppShell
      onNewChat={handleNewChat}
      searchQuery={searchQuery}
      onSearchChange={setSearchQuery}
      onSearchSubmit={() => undefined}
    >
      <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
        {(error || (resumeMode && state.clarificationPrompt)) && (
          <div className="shrink-0 space-y-2 px-container-padding pt-3">
            {error && (
              <div className="rounded-lg border border-error/30 bg-error-container/20 px-4 py-2 font-label-md text-on-error-container">
                {error}
              </div>
            )}
            {resumeMode && state.clarificationPrompt && (
              <div className="cyber-glass max-h-24 overflow-y-auto rounded-lg border border-secondary/30 p-3">
                <p className="mb-1 font-label-md font-bold text-secondary">
                  {t('deepResearch.clarification.title')}
                </p>
                <p className="font-body-md text-on-surface-variant">{state.clarificationPrompt}</p>
              </div>
            )}
          </div>
        )}

        <div className="grid min-h-0 flex-1 grid-rows-2 gap-element-gap p-container-padding">
          {/* Row 1: Execution Plan + Thinking Process */}
          <div className="grid min-h-0 grid-cols-1 grid-rows-2 gap-element-gap lg:grid-cols-3 lg:grid-rows-1">
            <section className="cyber-glass flex min-h-0 flex-col overflow-hidden rounded-xl p-4 lg:col-span-2">
              <h3 className={PANEL_HEADER}>
                <Icon name="account_tree" className="text-primary" />
                {t('deepResearch.executionPlan')}
              </h3>
              <div className={`${PANEL_BODY} pr-1`}>
                <div className="space-y-4">
                  <div className="rounded-lg border border-primary/20 bg-surface-container p-4">
                    <span className="mb-1 block text-[10px] font-bold uppercase tracking-widest text-primary">
                      {t('deepResearch.mainObjective')}
                    </span>
                    <p className="font-body-lg font-medium text-on-surface">
                      {state.researchTopic || state.question || t('deepResearch.mainObjectiveDesc')}
                    </p>
                  </div>

                  {!hasStarted && (
                    <div className="rounded-lg border border-outline-variant/10 bg-surface-container-low p-6 text-center">
                      <Icon name="biotech" className="mb-2 text-[32px] text-primary opacity-60" />
                      <p className="font-body-md text-on-surface-variant">{t('deepResearch.welcome')}</p>
                    </div>
                  )}

                  <div className="task-branch relative space-y-4 pt-2 pl-4">
                    {hasStarted && visibleTasks.length === 0 && (
                      <p className="font-label-md text-on-surface-variant">
                        {t('deepResearch.tasksEmpty')}
                      </p>
                    )}
                    {visibleTasks.map((task) => (
                      <TaskRow key={task.id} task={task} />
                    ))}
                  </div>
                </div>
              </div>
            </section>

            <section className="cyber-glass flex min-h-0 flex-col overflow-hidden rounded-xl p-4">
              <h3 className={PANEL_HEADER}>
                <Icon name="terminal" className="text-secondary" />
                {t('deepResearch.thinkingProcess')}
              </h3>
              <div
                ref={terminalRef}
                className={`terminal-scroll ${PANEL_BODY} rounded-lg border border-outline-variant/10 bg-surface-container-lowest/50 p-3 font-mono-code text-[13px] text-on-surface-variant`}
              >
                {terminalLines.length === 0 ? (
                  <p className="text-primary opacity-60">{t('deepResearch.logs.empty')}</p>
                ) : (
                  <div className="space-y-2">
                    {terminalLines.map((line, index) => (
                      <p
                        key={line.id}
                        className={terminalLineClass(
                          line.tone,
                          index === terminalLines.length - 1,
                        )}
                      >
                        <span className="opacity-50">[{line.timestamp}]</span> {line.text}
                      </p>
                    ))}
                  </div>
                )}
              </div>
            </section>
          </div>

          {/* Row 2: Final Synthesis + Citations */}
          <div className="grid min-h-0 grid-cols-1 grid-rows-2 gap-element-gap lg:grid-cols-3 lg:grid-rows-1">
            <section className="cyber-glass flex min-h-0 flex-col overflow-hidden rounded-xl p-4 lg:col-span-2">
              <h3 className={PANEL_HEADER}>
                <Icon name="description" className="text-primary" />
                {t('deepResearch.finalSynthesisDraft')}
              </h3>
              <div
                className={`${PANEL_BODY} rounded-lg border border-outline-variant/10 bg-surface-container-low p-4 text-on-surface-variant`}
              >
                {state.report ? (
                  <>
                    <MarkdownContent
                      content={state.report}
                      isStreaming={state.phase === 'summarizing'}
                    />
                    {state.phase === 'summarizing' && (
                      <div className="mt-4 space-y-2">
                        <div className="h-4 w-full animate-pulse rounded bg-surface-container-high opacity-40" />
                        <div className="h-4 w-3/4 animate-pulse rounded bg-surface-container-high opacity-40" />
                      </div>
                    )}
                  </>
                ) : (
                  <p className="font-body-md opacity-60">{t('deepResearch.report.empty')}</p>
                )}
              </div>
            </section>

            <section className="cyber-glass flex min-h-0 flex-col overflow-hidden rounded-xl p-4">
              <h3 className={PANEL_HEADER}>
                <Icon name="link" className="text-tertiary" />
                {t('deepResearch.citations')}
              </h3>
              <DeepResearchCitationList
                references={filteredReferences}
                className="min-h-0 flex-1"
              />
            </section>
          </div>
        </div>

        <ConversationInput
          placeholder={
            resumeMode
              ? t('deepResearch.input.resumePlaceholder')
              : state.phase === 'idle'
                ? t('deepResearch.input.placeholder')
                : t('deepResearch.input.refinePlaceholder')
          }
          disclaimer={t('deepResearch.footerDisclaimer')}
          sendTitle={resumeMode ? t('deepResearch.input.resume') : t('deepResearch.input.send')}
          onSend={handleSend}
          disabled={isStreaming}
          isStreaming={isStreaming}
          onStop={() => void stopGeneration()}
          layout="embedded"
          maxWidthClass="max-w-[900px]"
        />
      </div>
    </AppShell>
  )
}
