# GenChat — Product Overview

GenChat is an intelligent conversation platform built on Spring AI. It provides AI agent capabilities with web-connected search, file analysis, document generation, and deep research modules.

The current codebase implements the web search agent (ReAct pattern) which:
- Accepts user questions via a streaming SSE endpoint
- Performs web searches using Tavily MCP integration
- Returns structured streaming responses (thinking → text → references → recommended questions)
- Persists conversation sessions to MySQL for chat memory

The platform targets enterprise users and individual developers who need an out-of-the-box AI agent solution with session management, tool calling, and streaming output.
