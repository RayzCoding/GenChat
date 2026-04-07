# GenChat — Project Structure

Base package: `com.genchat`

```
src/main/java/com/genchat/
├── GenChatApplication.java        # Entry point, @MapperScan("com.genchat.repository")
├── agent/                         # Agent implementations (ReAct pattern)
│   └── WebSearchReactAgent.java   # Web search agent with streaming, tool calling, chat memory
├── common/                        # Shared utilities
│   ├── AgentResponse.java         # Unified streaming response format (text/thinking/reference/error/recommend)
│   └── JsonUtil.java              # Safe JSON field extraction helper
├── config/                        # Spring @Configuration classes
│   ├── MybatisPlusMetaObjectHandler.java  # Auto-fills createTime/updateTime
│   └── WebSearchToolInitConfig.java       # Initializes Tavily MCP client and tool callbacks
├── controller/                    # REST controllers
│   └── AgentController.java       # SSE streaming endpoint: GET /agent/chat/stream
├── converter/                     # MapStruct mappers (Entity ↔ DTO)
│   └── AiChatSessionConverter.java
├── dto/                           # Data Transfer Objects (Lombok @Builder)
│   └── AiChatSession.java
├── entity/                        # Domain/DB entities
│   ├── AgentState.java            # Holds accumulated search results per agent run
│   ├── AiChatSessionEntity.java   # MyBatis-Plus @TableName("ai_chat_session")
│   ├── RoundMode.java             # Enum: UNKNOWN, FINAL_ANSWER, TOOL_CALL
│   ├── RoundState.java            # Per-round execution state (mode, text buffer, tool calls)
│   └── SearchResult.java          # Record for parsed search results (url, title, content)
├── prompts/                       # System prompt templates (static string builders)
│   ├── BaseAgentPrompts.java      # Shared role definition, tool rules, output specs
│   └── ReactAgentPrompts.java     # Web search, file analysis, and recommendation prompts
├── repository/                    # MyBatis-Plus mapper interfaces (scanned via @MapperScan)
│   └── AiChatSessionRepository.java
└── service/                       # Business logic
    ├── AgentTaskService.java      # Manages running conversation tasks (ConcurrentHashMap)
    └── AiChatSessionService.java  # Session CRUD, extends ServiceImpl

src/main/resources/
├── application.yml                # Base configuration
├── application-local.yml          # Local dev profile
└── db/migration/                  # Flyway SQL migrations (versioned: V{timestamp}__description.sql)
```

## Conventions
- Agents are standalone classes (not Spring beans) — instantiated per request in the controller
- Entities use MyBatis-Plus annotations (`@TableName`, `@TableId`, `@TableField` with auto-fill)
- DTOs use Lombok `@Builder` pattern
- Converters use MapStruct with `Mappers.getMapper()` singleton pattern
- Streaming responses use Reactor `Sinks.Many<String>` wrapped in `Flux<String>`
- All streaming output is JSON-formatted via `AgentResponse` factory methods
- Repository interfaces extend `BaseMapper<Entity>` (MyBatis-Plus)
- Services extend `ServiceImpl<Repository, Entity>` (MyBatis-Plus)
- Flyway migration naming: `V{yyyyMMddHHmmss}__description.sql`
