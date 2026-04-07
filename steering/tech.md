# GenChat — Tech Stack & Build

## Language & Runtime
- Java 21
- Gradle (Groovy DSL) with Spring Boot plugin

## Core Frameworks
- Spring Boot 3.5.6
- Spring AI 1.1.0 (OpenAI-compatible starter + MCP client)
- Spring AI Alibaba Agent Framework 1.1.0.0

## Data & Persistence
- MySQL 8 via `mysql-connector-j`
- MyBatis-Plus 3.5.9 (CRUD, auto-fill timestamps via `MetaObjectHandler`)
- Flyway for schema migrations (`src/main/resources/db/migration/`)

## Key Libraries
- Lombok — boilerplate reduction (`@Data`, `@Builder`, `@Slf4j`, etc.)
- MapStruct 1.6.3 — entity ↔ DTO mapping (with `lombok-mapstruct-binding`)
- Alibaba fastjson2 — JSON serialization in `AgentResponse` and tool result parsing
- Jackson (`ObjectMapper`) — used alongside fastjson for `JsonNode` parsing
- Project Reactor — streaming via `Flux`, `Sinks.Many`, `Schedulers.boundedElastic()`

## External Services
- Tavily MCP — web search tool, configured via `tavily.api-key` and `tavily.mcp-url`
- OpenAI-compatible LLM endpoint (defaults to Alibaba DashScope / qwen-plus in local profile)

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
- `application.yml` — base config (datasource, AI keys, Flyway, Tavily)
- `application-local.yml` — local dev overrides (active by default via `spring.profiles.active: local`)
- API keys and base URLs are configurable via environment variables (`OPENAI_API_KEY`, `OPENAI_BASE_URL`)
