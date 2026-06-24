# GenChat — Project Structure

Base package: `com.genchat`

```
src/main/java/com/genchat/
├── GenChatApplication.java
├── agent/                         # Agent implementations
│   ├── core/                      # ReAct engine (AbstractReactAgent, ReactRoundScheduler, …)
│   ├── model/                     # Agent domain models (AgentState, RoundState, …)
│   ├── deepresearch/              # Plan-Execute deep research pipeline
│   │   └── model/                 # Deep-research state (OverAllState)
│   ├── WebSearchReactAgent.java
│   ├── FileReactAgent.java
│   ├── SkillsReactAgent.java
│   ├── DeepResearchAgent.java
│   ├── PPTBuilderAgent.java
│   └── SimpleReactAgent.java
├── application/
│   ├── agent/                     # AgentFacade, AgentFactory, PersistentChatAgent
│   ├── stream/                    # AgentStreamLifecycle, StreamChunkAccumulator, …
│   ├── validation/                # StreamRequestValidator (SSE param checks)
│   ├── strategy/                  # PPT state machine strategies
│   ├── FileApplication.java
│   └── tool/                      # SkillsTool, GrepTool, FileContentTool
│       └── grep/                  # Grep executors (ripgrep / Java fallback)
├── common/
│   ├── AgentStreamEvent.java      # SSE event protocol
│   ├── Result.java                # Unified REST wrapper
│   ├── utils/JacksonJson.java     # Unified JSON helper
│   └── prompts/                   # PromptLoader + thin Java wrappers
├── config/                        # Spring configuration + GenChatProperties
├── controller/                    # REST + SSE endpoints
├── service/                       # Business services
│   └── session/                   # SessionPayloadParser (reference / recommend)
└── repository/                    # MyBatis-Plus repositories

src/main/resources/
├── prompts/                       # Externalized markdown prompt templates
├── python/render_ppt.py           # PPT rendering script
├── db/migration/                  # Flyway SQL migrations
└── application.yml                # Base config (secrets via env vars)
```

## API Surface

| Prefix | Controller | Notes |
|--------|------------|-------|
| `/agent/**` | AgentController | SSE streaming chat (6 agent modes) + `/agent/stop` |
| `/agent/sessions/**` | SessionController | Session list, search, detail, delete; returns `Result<T>` |
| `/file/**` | FileController | Upload, list, delete files |

## Agent Modes

1. Web search (`/agent/chat/stream`)
2. File Q&A (`/agent/file/stream`)
3. PPT generation (`/agent/ppt/stream`)
4. Deep research (`/agent/deep/stream`)
5. Skills assistant (`/agent/skills/stream`)
6. Simple ReAct (`/agent/simple/stream`)

Deep/ppt/skills endpoints accept both `conversationId` and `conversationsId` as session ID aliases.

## Frontend

React + Vite app under `frontend/`. Proxies `/agent` and `/file` to the backend in dev.
