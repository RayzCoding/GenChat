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

GenChat is a intelligent conversation application platform built on Spring AI, integrating four core functional modules: web-connected search, file processing, document generation, and deep research. The platform aims to provide enterprise users and individual developers with an out-of-the-box, highly scalable AI agent solution.

**Project Vision**: To build a comprehensive, easy-to-integrate, production-ready intelligent conversation platform that helps enterprises create smarter business applications in the AI era.

### ✨ Platform Highlights
- 🚀 **Comprehensive Features**: Covers multi-level AI application scenarios from basic conversations to complex research
- 🏗️ **Engineering Architecture**: Adopts microservices architecture supporting high concurrency and scalable deployment
- 🔌 **Modular Design**: Four core functional modules support independent use or combined deployment
- 🔧 **Developer Friendly**: Based on Spring Boot ecosystem with complete API documentation and development tools
- 📦 **Out-of-the-Box**: Pre-configured with enterprise best practices for rapid AI capability integration

## 🔥 Core Features

### 🌐 Intelligent Web-Connected Conversation
- **Real-time Information Acquisition**: Integrates search engines to break through LLM knowledge cutoff limitations
- **Structured Output**: Four-stage interaction design (thinking process → answer text → reference sources → recommended questions)
- **Streaming Response Control**: Supports real-time streaming output and intelligent interruption control
- **Information Traceability**: All answers include authoritative reference sources ensuring information reliability

### 📁 Multimodal File Analysis
- **Full Format Support**: PDF, DOCX, TXT, PNG, JPG, and other common document formats
- **Intelligent Content Extraction**: Implements efficient semantic retrieval for large files using RAG technology
- **Image Text Recognition**: Parses text information in images through multimodal large language models
- **Context Awareness**: Automatic association between files and conversation sessions supporting multi-turn deep Q&A

### 📊 Intelligent Document Generation
- **Requirement-Driven Generation**: Automatically generates professional PPT documents based on natural language descriptions
- **Multi-mode Output**:
  - Template Fill Mode: Balances aesthetics with editability
  - Text-to-Image Mode: Generates visually stunning presentations
  - HTML to PPT: Flexible style and layout customization
- **Intelligent State Management**: Full-process state control from requirement clarification to final output
- **Automatic Image Generation**: Integrates text-to-image services to enrich document visual effects

### 🔍 Deep Research Analysis
- **Intelligent Planning and Execution**: Automatically decomposes complex problems based on Plan-Execute pattern
- **Parallel Processing Optimization**: Multi-task parallel execution significantly improves research efficiency
- **Iterative Optimization Mechanism**: Evaluation-Adjustment-Re-execution loop optimization strategy
- **Automatic Problem Optimization**: Intelligently enriches and optimizes user's original problem statements

## 🛠️ Technology Stack

### Base Frameworks
| Component | Version | Core Functions |
|-----------|---------|----------------|
| Spring Boot | 3.5.6 | Application base framework providing auto-configuration, dependency injection, Web container, etc. |
| Spring AI | 1.1.0 | AI application development framework encapsulating LLM calls, tool integration, prompt management, etc. |
| MyBatis-Plus | 3.5.5 | Simplifies MySQL operations, provides CRUD encapsulation, pagination, conditional queries, etc. |

### Large Model Services
| Model Name | Type | Main Purpose |
|------------|------|--------------|
| qwen-plus | Language Model | Core large model responsible for natural language understanding, logical reasoning, content generation |
| qwen3-vl-plus | Multimodal Model | Full-modal large model with image recognition capability for image content parsing |
| nanobanana-pro | Text-to-Image Model | Generates high-definition 4K images, excellent quality, used for high-quality PPT illustrations |
| qwen-image-plus | Text-to-Image Model | Generates simple illustrations, low cost, used for general image generation needs |
| embedding Model | Vector Model | Used for large file vectorization, supports semantic retrieval for file Q&A |

### Data Storage Components
| Component | Version | Purpose |
|-----------|---------|---------|
| MySQL | 8.0.33 | Stores structured data: session records, file metadata, PPT instance status, template configurations, etc. |
| MinIO | 8.5.1 | Object storage service: stores user-uploaded files, generated PPT files, images, and other binary data |
| PgVector | - | Vector storage for file Q&A, stores chunked content vectors of large files |

### Tools and Middleware
| Category | Component | Version | Purpose |
|----------|-----------|---------|---------|
| Tool Integration | MCP | - | Model Context Protocol, standard protocol for tool integration, unifies tool calls like search engines, file retrieval |
| File Processing | Apache PDFBox | 2.0.30 | PDF file text extraction |
| File Processing | Apache POI | 5.2.5 | Word/Excel file parsing |
| File Processing | python-pptx | - | Python scripts for generating PPT files |
| Streaming Processing | Reactor | 3.7.11 | Controls agent streaming output start/stop, reactive programming framework |

## ⭐ Star History

Thank you to everyone who has starred this project! Your recognition is our motivation for continuous improvement.

[![Star History Chart](https://api.star-history.com/svg?repos=RayzCoding/GenChat&type=Date)](https://star-history.com/#RayzCoding/GenChat&Date)

---

<p align="center">
  Made with ❤️ by GenChat Team
</p>