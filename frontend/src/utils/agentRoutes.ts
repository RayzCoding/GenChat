const AGENT_ROUTES: Record<string, string> = {
  webSearchReactAgent: '/chat',
  fileReactAgent: '/file-qa',
  deepResearchAgent: '/deep-research',
  pptBuilderAgent: '/ppt',
  skillsReactAgent: '/skills',
}

export function getRouteForAgentType(agentType?: string): string {
  if (agentType && AGENT_ROUTES[agentType]) {
    return AGENT_ROUTES[agentType]
  }
  return '/chat'
}
