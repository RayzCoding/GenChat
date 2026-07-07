# GenChat — Product Overview

GenChat is an intelligent conversation platform built on Spring AI. It provides agent capabilities across web-connected search, file analysis, document generation, deep research, skills assistance, and simple ReAct chat.

The current codebase implements multiple streaming agent modes, including web search, file Q&A, PPT generation, deep research, skills assistant, and simple ReAct. These agents:
- Accept user questions through SSE endpoints under `/agent/*/stream`
- Use Tavily MCP integration for web search when needed
- Return structured streaming responses (thinking → text → references → recommended questions)
- Persist conversation sessions to MySQL for chat memory and session retrieval

The platform targets enterprise users and individual developers who need an out-of-the-box AI agent solution with session management, tool calling, and streaming output.
