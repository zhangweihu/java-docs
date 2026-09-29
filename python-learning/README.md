# Python 学习指南（从基础到实战）

> 本文档是与 [java-learning](../java-learning/README.md) 并列的 Python 体系化学习资料。面向**已有 Java 基础的读者**，全程穿插 Python vs Java 对比，帮助快速迁移。每一章都配有**可直接运行**的代码示例，建议亲手敲一遍并完成章节末尾练习。
>
> 定位：Java 负责高性能企业后端，Python 负责脚本自动化、数据分析、AI 与快速 Web 开发（FastAPI/Django）——两套体系互补。
>
> ✅ **9 章基础体系 + 10 章 AI/工程化扩展（LLM 基础 / Agent / RAG / 编排 / MCP / 机器学习 / 语音多模态 / 评测 / AutoGen / Harness）已全部发布**，从基础语法到 AI Agent 全栈再到生产质量护栏一条龙学完。

## 目录结构

| 章节 | 内容 | 文件 | 状态 |
| --- | --- | --- | --- |
| 第一章 | Python 基础与核心语法：环境搭建、动态类型、流程控制、四大数据结构、函数、装饰器、面向对象、异常文件、标准库、命令行实战 | [01-Python基础.md](./01-Python基础.md) | ✅ 已发布 |
| 第二章 | 进阶语法：迭代器与生成器深挖、上下文管理器（contextlib）、函数式编程（闭包/装饰器/functools）、元类与动态特性、类型注解（Type Hints/dataclass） | [02-进阶语法.md](./02-进阶语法.md) | ✅ 已发布 |
| 第三章 | 文件处理与标准库深入：pathlib 全解、CSV/JSON/Excel 处理、日志 logging、配置管理（.env/TOML/YAML）、压缩归档、hashlib/uuid 等 | [03-文件与标准库.md](./03-文件与标准库.md) | ✅ 已发布 |
| 第四章 | 并发与异步：GIL 原理、多线程与锁、线程池、多进程、asyncio 协程、并发选型实战 | [04-并发与异步.md](./04-并发与异步.md) | ✅ 已发布 |
| 第五章 | 网络编程与爬虫：HTTP 基础、requests、正则、BeautifulSoup、反爬与合规、并发爬虫、Scrapy、采集实战 | [05-网络与爬虫.md](./05-网络与爬虫.md) | ✅ 已发布 |
| 第六章 | Web 开发：FastAPI 快速上手（对标 Spring Boot）、路由与参数、Pydantic 校验、依赖注入、SQLAlchemy、部署、图书管理实战 | [06-FastAPI-Web开发.md](./06-FastAPI-Web开发.md) | ✅ 已发布 |
| 第七章 | 数据分析：NumPy 数值计算、Pandas（Series/DataFrame、数据清洗、分组聚合、透视表）、Matplotlib 可视化、销售分析实战 | [07-数据分析.md](./07-数据分析.md) | ✅ 已发布 |
| 第八章 | 自动化脚本与工程化：文件批处理（pathlib/shutil）、subprocess、定时任务（cron/schedule）、命令行工具（argparse/click）、日志容错、清理备份实战 | [08-自动化脚本.md](./08-自动化脚本.md) | ✅ 已发布 |
| 第九章 | 工程化与测试：虚拟环境与依赖管理、PEP 8（ruff/black）、mypy 类型检查、pytest 全解（fixture/参数化/mock/覆盖率）、打包发布（pyproject.toml）、CI/CD、工程化实战 + 体系总结 | [09-工程化与测试.md](./09-工程化与测试.md) | ✅ 已发布 |
| 第十章 | AI Agent 开发：Agent 原理与 ReAct 循环、openai SDK（Chat Completions/流式/结构化输出）、Function Calling 全流程、手写 Agent Loop、OpenAI Agents SDK（Agent/Runner/@function_tool）、多 Agent 协作（handoffs/guardrails/Session/Tracing）、智能客服实战、安全成本工程化 | [10-OpenAI-Agents.md](./10-OpenAI-Agents.md) | ✅ 已发布 |
| 第十一章 | RAG 与向量数据库：知识三缺陷、Embedding 向量化、余弦相似度、向量库选型（Chroma/pgvector）、手写最小 RAG、切块/元数据过滤/混合检索/Rerank/评估、本地文档问答实战 | [11-RAG与向量数据库.md](./11-RAG与向量数据库.md) | ✅ 已发布 |
| 第十二章 | LangGraph 编排：状态图（State/Node/Edge）、条件路由与循环、工具调用 Agent 节点、checkpointer 记忆持久化、Supervisor 多 Agent 工作流、客服+工具+人工兜底实战 | [12-LangGraph编排.md](./12-LangGraph编排.md) | ✅ 已发布 |
| 第十三章 | MCP 工具接入：协议原理（Host/Client/Server）、Tools/Resources/Prompts、FastMCP 搭建 Server、openai-agents/LangGraph/原生客户端三端接入、客服工具 MCP 化实战、生态与安全 | [13-MCP工具接入.md](./13-MCP工具接入.md) | ✅ 已发布 |
| 第十四章 | 机器学习入门：监督/无监督三任务、scikit-learn 统一 API（fit/predict）、回归/分类评估指标、特征工程（编码/标准化/防泄漏）、KMeans/PCA、交叉验证与 GridSearchCV、客户流失预测实战 | [14-机器学习.md](./14-机器学习.md) | ✅ 已发布 |
| 第十五章 | 多模态语音 Agent：多模态三形态、STT（gpt-4o-transcribe）、TTS（gpt-4o-mini-tts）、Realtime 全双工、图像理解与生成、语音问答助手实战、延迟/合规 | [15-多模态语音Agent.md](./15-多模态语音Agent.md) | ✅ 已发布 |
| 第十六章 | Agent 评测体系：评测金字塔（单元/集成/E2E/线上）、任务/流程/成本指标、Golden Set、LLM-as-Judge（rubric/成对比较/偏差防护）、pytest+CI 回归、客服评测套件实战 | [16-Agent评测体系.md](./16-Agent评测体系.md) | ✅ 已发布 |
| 第十七章 | AutoGen 多 Agent 协作：三层架构 + AssistantAgent + Reflection 双 Agent 迭代 + RoundRobin/SelectorGroupChat 群聊调度 + FunctionTool/MCP 整合 + DiGraphBuilder 显式拓扑 + 人机协同 + Console 观测 + AutoGen vs LangGraph 选型 | [17-AutoGen多Agent协作.md](./17-AutoGen多Agent协作.md) | ✅ 已发布 |
| 第十八章 | Python Harness 实战：测试 Harness 五大组件 + pytest fixture 全套（scope/嵌套/conftest）+ 参数化 + mock（pytest-mock/respx）+ 覆盖率（行/分支/增量）+ 变异测试（mutmut）+ 属性测试（Hypothesis）+ AI Agent Harness 工具调用断言 + LLM-as-Judge + 录制回放 + Langfuse 观测 + 成本延迟门禁 + AutoGen 项目 Harness 实战 + GitHub Actions CI | [18-PythonHarness实战.md](./18-PythonHarness实战.md) | ✅ 已发布 |
| 第十九章 | LLM 基础与应用：语言模型与 Transformer 直觉、Token/上下文窗口、预训练与对齐、生成参数、Python 调用/流式/结构化输出、提示词、安全验证、模型选型与成本延迟、自研 LLM 技术栈与微调/训练路线、工单摘要实战 | [19-LLM基础与应用.md](./19-LLM基础与应用.md) | ✅ 已发布 |

## 学习路线

```
基础语法 ──► 进阶语法/函数式 ──► 文件与标准库 ──► 并发与异步 ──► 网络/爬虫 ──► Web 开发 ──► 数据分析
   │             │                  │                │            │            │          │
   └─────────────┴─── 主线：语法 → 库 → 工程（每章都动手敲代码）────────┴───────────┴──────────┘

自动化脚本 ──► 工程化/测试（穿插学习：写脚本时顺手用上）

AI 扩展（需前 9 章基础）──► ⑲ LLM 基础 ──► ⑩ Agent 开发 ──► ⑪ RAG/向量库 ──► ⑫ LangGraph 编排 ──► ⑬ MCP 工具
    │
    └──────────► ⑭ 机器学习 ──► ⑮ 语音多模态 ──► ⑯ Agent 评测（质量闭环）──► ⑰ AutoGen 多 Agent ──► ⑱ Python Harness（生产质量护栏收官）
```

## 学习建议

1. **先跑通第一章**：Python 语法量小但细节多（缩进、动态类型、推导式、装饰器），务必动手；
2. **对照 Java 学**：把 Java 里熟悉的概念（List → list、HashMap → dict、Stream → 推导式、Lambda → lambda、注解+AOP → 装饰器）映射过去；
3. **按需深入**：后端工程师优先学 3/4/5/6 章（脚本 + 服务）；数据分析方向优先 7 章；AI 工程师建议先读第 19 章建立 LLM 基础，再按需学习第 10~18 章（Agent 全栈 + Harness）；
4. **实践出真知**：每章结尾的练习题 + 综合实战（如第一章学生成绩管理系统）必须独立完成；
5. **多 Agent 与质量护栏是"上线前最后两公里"**：第 17 章学会编排复杂 Agent 协作，第 18 章学会为它装上"质量护栏"——这两章合起来是从 Demo 到生产的关键。

## 配套环境

- Python 3.10+（推荐 3.11/3.12）：https://www.python.org/downloads/
- 编辑器：VS Code + Python 插件 或 PyCharm Community
- 每个项目使用独立虚拟环境：`python -m venv venv`

下一章：[01-Python基础.md](./01-Python基础.md)
