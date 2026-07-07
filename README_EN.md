# GenChat - Intelligent Conversation Application Platform

<p align="center">
  <a href="README.md">中文</a> | <a href="README_EN.md">English</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Version-1.0.0-blue" alt="Version">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5.6-green" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Spring%20AI-1.1.0-orange" alt="Spring AI">
  <img src="https://img.shields.io/github/stars/RayzCoding/GenChat" alt="Stars">
  <img src="https://img.shields.io/github/forks/RayzCoding/GenChat" alt="Forks">
  <img src="https://img.shields.io/github/issues/RayzCoding/GenChat" alt="Issues">
</p>

## 🎯 Project Introduction

GenChat is an intelligent conversation application platform built on Spring AI. It offers multiple agent modes—web search, file Q&A, PPT generation, deep research, and a Skills assistant—along with a React frontend. The platform aims to provide enterprise users and individual developers with an out-of-the-box, highly scalable AI agent solution.

**Project Vision**: To build a comprehensive, easy-to-integrate, production-ready intelligent conversation platform that helps enterprises create smarter business applications in the AI era.

### ✨ Platform Highlights
- 🚀 **Comprehensive Features**: Covers multi-level AI application scenarios from basic conversations to complex research
- 🏗️ **Modular Architecture**: Backend layered as controller → application → agent/service, with six agent modes that can evolve independently
- 🔌 **Composable Capabilities**: Web search, RAG file Q&A, PPT state machine, Plan-Execute deep research, and more—usable standalone or combined
- 🔧 **Developer Friendly**: Built on the Spring Boot ecosystem with clear REST / SSE APIs and externalized configuration
- 📦 **Out-of-the-Box**: Session management, streaming stop, Flyway migrations, and a Vite + React frontend included

## 🔥 Core Features

### 🌐 Intelligent Web-Connected Conversation
- **Real-time Information Acquisition**: Integrates search engines to break through LLM knowledge cutoff limitations
- **Structured Output**: Four-stage interaction design (thinking process → answer text → reference sources → recommended questions)
- **Streaming Response Control**: Supports real-time streaming output and intelligent interruption control
- **Information Traceability**: All answers include authoritative reference sources ensuring information reliability

<p>
  <a href="images/ai-chat-1.png"><img src="images/ai-chat-1.png" alt="Web conversation page - 1" width="100%" /></a>
</p>
<p>
  <a href="images/ai-chat-2.png"><img src="images/ai-chat-2.png" alt="Web conversation page - 2" width="100%" /></a>
</p>
<p><em>Search and answer in one flow with transparent reasoning, structured output, and traceable references.</em></p>

### 📁 Multimodal File Analysis
- **Full Format Support**: PDF, DOCX, TXT, PNG, JPG, and other common document formats
- **Intelligent Content Extraction**: Implements efficient semantic retrieval for large files using RAG technology
- **Image Text Recognition**: Parses text information in images through multimodal large language models
- **Context Awareness**: Automatic association between files and conversation sessions supporting multi-turn deep Q&A

<p>
  <a href="images/file-qa-1.png"><img src="images/file-qa-1.png" alt="File Q&A page - 1" width="100%" /></a>
</p>
<p>
  <a href="images/file-qa-2.png"><img src="images/file-qa-2.png" alt="File Q&A page - 2" width="100%" /></a>
</p>
<p>
  <a href="images/file-qa-3.png"><img src="images/file-qa-3.png" alt="File Q&A page - 3" width="100%" /></a>
</p>
<p><em>Upload files and continue multi-turn Q&A with semantic retrieval and multimodal understanding for dense content.</em></p>

### 📊 Intelligent Document Generation
- **Requirement-Driven Generation**: Automatically generates professional PPT documents based on natural language descriptions
- **Multi-mode Output**:
  - Template Fill Mode: Balances aesthetics with editability
  - Text-to-Image Mode: Generates visually stunning presentations
  - HTML to PPT: Flexible style and layout customization
- **Intelligent State Management**: Full-process state control from requirement clarification to final output
- **Automatic Image Generation**: Integrates text-to-image services to enrich document visual effects

<p>
  <a href="images/ppt-1.png"><img src="images/ppt-1.png" alt="PPT generation page - 1" width="100%" /></a>
</p>
<p>
  <a href="images/ppt-2.png"><img src="images/ppt-2.png" alt="PPT generation page - 2" width="100%" /></a>
</p>
<p>
  <a href="images/ppt-3.png"><img src="images/ppt-3.png" alt="PPT generation page - 3" width="100%" /></a>
</p>
<p><em>From requirement clarification to export, generate editable presentations with multiple modes and automatic visuals.</em></p>

### 🔍 Deep Research Analysis
- **Intelligent Planning and Execution**: Automatically decomposes complex problems based on Plan-Execute pattern
- **Parallel Processing Optimization**: Multi-task parallel execution significantly improves research efficiency
- **Iterative Optimization Mechanism**: Evaluation-Adjustment-Re-execution loop optimization strategy
- **Automatic Problem Optimization**: Intelligently enriches and optimizes user's original problem statements

<p>
  <a href="images/deep-research.png"><img src="images/deep-research.png" alt="Deep research page" width="100%" /></a>
</p>
<p><em>Use a plan-execute loop with parallel tasks to decompose complex topics, synthesize evidence, and iterate results.</em></p>

### 🧩 Skills Assistant
- **Extensible Skill Directory**: Load custom skills via `skills.directory` and compose tool capabilities on demand
- **Multi-tool Collaboration**: Integrates web search, file retrieval, Grep, and more for complex automation tasks
- **Streaming Interaction**: Same SSE streaming output and stop control as other agents

<p>
  <a href="images/skills-1.png"><img src="images/skills-1.png" alt="Skills page - 1" width="100%" /></a>
</p>
<p>
  <a href="images/skills-2.png"><img src="images/skills-2.png" alt="Skills page - 2" width="100%" /></a>
</p>
<p><em>Extend custom skills by directory and orchestrate tools for robust search, analysis, and automation workflows.</em></p>

## 🚀 Quick Start

```bash
# Backend (default port 8080, requires Java 21)
./gradlew bootRun

# Frontend (dev server at http://localhost:5173)
cd frontend && npm install && npm run dev
```

Configure MySQL, Redis, OpenAI-compatible API, Tavily, MinIO, PgVector, and other connections in `application.yml` (overridable via environment variables). Agent behavior can be tuned via `genchat.*` settings (e.g. ReAct max rounds, chat memory window, file chunk size).

See [`frontend/README.md`](frontend/README.md) for more frontend details.

## 🛠️ Technology Stack

### Base Frameworks
| Component | Version | Core Functions |
|-----------|---------|----------------|
| Java | 21 | Runtime |
| Spring Boot | 3.5.6 | Application base framework providing auto-configuration, dependency injection, Web container, etc. |
| Spring AI | 1.1.0 | AI application development framework encapsulating LLM calls, tool integration, prompt management, etc. |
| MyBatis-Plus | 3.5.9 | Simplifies MySQL operations, provides CRUD encapsulation, pagination, conditional queries, etc. |
| Flyway | - | Database schema migrations (`db/migration`) |

### Large Model Services
| Model Name | Type | Main Purpose |
|------------|------|--------------|
| qwen-plus | Language Model | Core large model for NLU, reasoning, and content generation (other models via OpenAI-compatible API) |
| qwen3-vl-plus | Multimodal Model | Full-modal large model with image recognition capability for image content parsing |
| nanobanana-pro | Text-to-Image Model | Generates high-definition 4K images, excellent quality, used for high-quality PPT illustrations |
| qwen-image-plus | Text-to-Image Model | Generates simple illustrations, low cost, used for general image generation needs |
| embedding Model | Vector Model | Used for large file vectorization, supports semantic retrieval for file Q&A |

### Data Storage Components
| Component | Version | Purpose |
|-----------|---------|---------|
| MySQL | 8.0.33 | Stores structured data: session records, file metadata, PPT instance status, template configurations, etc. |
| Redis | - | Distributed agent task state and cross-instance stop signals (Redisson) |
| MinIO | 8.6.0 | Object storage service: stores user-uploaded files, generated PPT files, images, and other binary data |
| PgVector | - | Vector storage for file Q&A, stores chunked content vectors of large files |

### Tools and Middleware
| Category | Component | Version | Purpose |
|----------|-----------|---------|---------|
| Tool Integration | MCP | - | Model Context Protocol, standard protocol for tool integration, unifies tool calls like search engines, file retrieval |
| File Processing | Apache PDFBox | 3.0.4 | PDF file text extraction |
| File Processing | Apache POI | 5.3.0 | Word/Excel file parsing |
| File Processing | python-pptx | - | Python scripts for generating PPT files |
| Streaming Processing | Reactor | - | SSE streaming output and agent task lifecycle management |
| Frontend | React + Vite | 19 / 6 | Web UI with zh-CN / en-US support |

## ⭐ Star History

Thank you to everyone who has starred this project! Your recognition is our motivation for continuous improvement.

[![Star History Chart](https://api.star-history.com/svg?repos=RayzCoding/GenChat&type=Date)](https://star-history.com/#RayzCoding/GenChat&Date)

---

<p align="center">
  Made with ❤️ by GenChat Team
</p>
