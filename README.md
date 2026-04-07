# GenChat - 智能对话应用平台
<p align="center">
  <a href="README.md">中文</a> | <a href="README_EN.md">English</a>
</p>
<p align="center">
  <img src="https://img.shields.io/badge/Version-1.0.0-blue" alt="版本">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5.6-green" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Spring%20AI-1.1.0-orange" alt="Spring AI">
  <img src="https://img.shields.io/github/stars/RayzCoding/GenChat" alt="Stars">
  <img src="https://img.shields.io/github/forks/RayzCoding/GenChat" alt="Forks">
  <img src="https://img.shields.io/github/issues/RayzCoding/GenChat" alt="Issues">
</p>

## 🎯 项目简介

GenChat 是一个基于 Spring AI 构建的智能对话应用平台，集成了联网搜索、文件处理、文档生成和深度研究四大核心功能模块。平台旨在为企业用户和个人开发者提供一个开箱即用、高度可扩展的 AI 智能体解决方案。

**项目愿景**：打造一个功能全面、易于集成、生产可用的智能对话平台，助力企业在 AI 时代构建更智能的业务应用。

### ✨ 平台特色
- 🚀 **功能全面**：覆盖从基础对话到复杂研究的多层次AI应用场景
- 🏗️ **工程化架构**：采用微服务架构，支持高并发、可扩展部署
- 🔌 **模块化设计**：四大核心功能模块支持独立使用或组合部署
- 🔧 **开发者友好**：基于Spring Boot生态，提供完整的API文档和开发工具
- 📦 **开箱即用**：预置企业级最佳实践，快速实现AI能力集成

## 🔥 核心功能

### 🌐 智能联网对话
- **实时信息获取**：集成搜索引擎，突破大模型知识截止限制
- **结构化输出**：四段式交互设计（思考过程→回答正文→参考来源→推荐问题）
- **流式响应控制**：支持实时流式输出和智能中断控制
- **信息可追溯**：所有回答附带权威参考来源，确保信息可靠性

### 📁 多模态文件分析
- **全格式支持**：PDF、DOCX、TXT、PNG、JPG等常见文档格式
- **智能内容提取**：基于RAG技术实现大文件的高效语义检索
- **图片文字识别**：通过多模态大模型解析图片中的文字信息
- **上下文感知**：文件与对话会话自动关联，支持多轮深入问答

### 📊 智能文档生成
- **需求驱动生成**：基于自然语言描述自动生成专业PPT文档
- **多模式输出**：
  - 模板填充模式：兼顾美观与可编辑性
  - 文生图模式：生成视觉效果出众的演示文稿
  - HTML转PPT：灵活定制样式布局
- **智能状态管理**：从需求澄清到成品输出的全流程状态控制
- **自动配图生成**：集成文生图服务，丰富文档视觉效果

### 🔍 深度研究分析
- **智能规划执行**：基于Plan-Execute模式，自动拆解复杂问题
- **并行处理优化**：多任务并行执行，大幅提升研究效率
- **迭代优化机制**：评估-调整-再执行的循环优化策略
- **问题自动优化**：智能丰富和优化用户原始问题表述

## 🛠️ 技术栈

### 基础框架
| 组件 | 版本 | 核心作用 |
|------|------|----------|
| Spring Boot | 3.5.6 | 应用基础框架，提供自动配置、依赖注入、Web容器等核心能力 |
| Spring AI | 1.1.0 | AI应用开发框架，封装LLM调用、工具集成、Prompt管理等能力 |
| MyBatis-Plus | 3.5.5 | 简化MySQL操作，提供CRUD封装、分页、条件查询等能力 |

### 大模型服务
| 模型名称 | 类型 | 主要用途 |
|----------|------|----------|
| qwen-plus | 语言模型 | 核心大模型，负责自然语言理解、逻辑推理、内容生成 |
| qwen3-vl-plus | 多模态模型 | 全模态大模型，具备图像识别能力，用于图片内容解析 |
| nanobanana-pro | 文生图模型 | 生成高清4K图，效果好，用于PPT生成中的高质量配图 |
| qwen-image-plus | 文生图模型 | 生成简单配图，成本低，用于一般性图片生成需求 |
| embedding模型 | 向量模型 | 用于大文件向量化，支持文件问答的语义检索 |

### 数据存储组件
| 组件 | 版本 | 用途 |
|------|------|------|
| MySQL | 8.0.33 | 存储结构化数据：会话记录、文件元信息、PPT实例状态、模板配置等 |
| MinIO | 8.5.1 | 对象存储服务：存储用户上传的文件、生成的PPT文件、图片等二进制数据 |
| PgVector | - | 向量存储，用于文件问答，存储大文件的分块内容向量 |

### 工具与中间件
| 分类 | 组件 | 版本 | 用途 |
|------|------|------|------|
| 工具集成 | MCP | - | Model Context Protocol，工具集成的标准协议，统一搜索引擎、文件检索等工具调用 |
| 文件处理 | Apache PDFBox | 2.0.30 | PDF文件文本提取 |
| 文件处理 | Apache POI | 5.2.5 | Word/Excel文件解析 |
| 文件处理 | python-pptx | - | 生成PPT文件的Python脚本 |
| 流式处理 | Reactor | 3.7.11 | 控制智能体的流式输出启停，响应式编程框架 |

## ⭐ Star History

感谢所有Star本项目的小伙伴！您的认可是我们持续改进的动力。

[![Star History Chart](https://api.star-history.com/svg?repos=RayzCoding/GenChat&type=Date)](https://star-history.com/#RayzCoding/GenChat&Date)

---

<p align="center">
  Made with ❤️ by GenChat Team
</p>