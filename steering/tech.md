# GenChat — Tech Stack & Build

## Language & Runtime
- Java 21
- Gradle (Groovy DSL) with Spring Boot plugin

## Core Frameworks
- Spring Boot 3.5.6
- Spring AI 1.1.0 (OpenAI-compatible starter + MCP client + PgVector vector store)

## Data & Persistence
- MySQL 8 via `mysql-connector-j`
- MyBatis-Plus 3.5.9 (CRUD, auto-fill timestamps via `MetaObjectHandler`)
- Flyway for schema migrations (`src/main/resources/db/migration/`)
- Redis via Spring Data Redis + Redisson (distributed agent task registry and stop signals)
- PgVector for file RAG embeddings

## Key Libraries
- Lombok — boilerplate reduction (`@Data`, `@Builder`, `@Slf4j`, etc.)
- MapStruct 1.6.3 — entity ↔ DTO mapping (with `lombok-mapstruct-binding`)
- Jackson — unified JSON handling via `JacksonJson` (SSE events, session payloads, tool results)
- Project Reactor — streaming via `Flux`, `Sinks.Many`, `Schedulers.boundedElastic()`
- MinIO 8.6.0 — object storage for uploads and generated assets
- Apache PDFBox 3.0.4 / Apache POI 5.3.0 — document parsing

## External Services
- Tavily MCP — web search tool, configured via `tavily.api-key` and `tavily.mcp-url`
- OpenAI-compatible LLM endpoint (`OPENAI_API_KEY`, `OPENAI_BASE_URL`)
- Optional image generation provider (`grsai.nanobanana.api-key`)

## Common Commands

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Start the application
./gradlew bootRun

# Clean build artifacts
./gradlew clean
```

## Configuration
- `application.yml` — base config (datasource, Redis, AI keys, Flyway, Tavily, MinIO, PgVector, `genchat.*`)
- Secrets and endpoints are overridable via environment variables (`MYSQL_*`, `REDIS_*`, `OPENAI_*`, `TAVILY_*`, `MINIO_*`, `PGVECTOR_*`, etc.)

### `genchat.*` agent settings (defaults in `application.yml`)
| Key | Default | Purpose |
|-----|---------|---------|
| `genchat.agent.max-rounds` | 1 | ReAct round limit for web search / skills agents |
| `genchat.agent.max-retries` | 0 | LLM stream retry count on transient errors |
| `genchat.agent.chat-memory-size` | 30 | Conversation history window for persistent agents |
| `genchat.file.chunk-size` | 500 | Large-file embedding chunk size (chars) |
| `genchat.file.chunk-overlap` | 50 | Chunk overlap for RAG splitting |
| `genchat.deep-research.max-rounds` | 1 | Deep research plan-execute loop limit |
| `genchat.deep-research.tool-semaphore-permits` | 1 | Concurrent tool call limit in deep research |

Other notable keys: `skills.directory`, `ppt.python-script`, `app.cors.allowed-origin-patterns`.

## Frontend
- React 19 + Vite 6 under `frontend/`
- Dev proxy: `/agent/*` and `/file/*` → `http://localhost:8081`
- See `frontend/README.md` for local setup
