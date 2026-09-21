# 第十七章 AutoGen 编排：把"多 Agent 复杂协作"交给微软框架

> 本章目标：第 10 章用 OpenAI Agents SDK 写单 Agent，第 12 章用 LangGraph 把流程画成状态图——但**当业务需要 3 个以上 Agent 协作、互相讨论、分工决策、动态角色**时，就需要更专业的**多 Agent 编排框架**。本章掌握微软的 **AutoGen（autogen-agentchat）**：用最少的代码表达"多专家对话式协作"，理解 `AssistantAgent` / `GroupChat` / `RoundRobinGroupChat` / `SelectorGroupChat` 等核心抽象，并完成一个**多 Agent 协同完成市场调研报告**的实战项目。
>
> 前置知识：第 10 章（OpenAI Agents SDK）、第 12 章（LangGraph 状态图）、第 13 章（MCP）。对标 Java：相当于"Multi-Agent 系统设计模式"，类似 Akka Actor 集群协作、JADE（Java Agent Development Framework），但用 LLM 作为"对话协议"。
>
> 环境准备：`pip install "autogen-agentchat[openai]" autogen-ext`，设置 `OPENAI_API_KEY` 或兼容的 Azure/本地模型 endpoint。

---

## 17.1 为什么需要"多 Agent 框架"——从单 Agent 到多 Agent

### 17.1.1 单 Agent 的局限

第 10 章的 `Runner.run(agent, message)` 是"一个专家干全部事"：

```
用户 ──► [万能 Agent（搜索+计算+写作+代码）] ──► 答案
```

**三大瓶颈**：

| 瓶颈 | 表现 | 后果 |
| --- | --- | --- |
| **Prompt 臃肿** | 系统提示塞 2000 字规则 | 模型注意力分散，关键指令被忽略 |
| **工具冲突** | 同时塞 10 个工具 | 模型选错工具的概率上升（"工具幻觉"） |
| **风格串味** | 写代码语气 + 写报告语气 + 写邮件语气混在一起 | 输出风格不可控，QA 抓狂 |

### 17.1.2 多 Agent 的核心思想：分工

把"一个超级 Agent"拆成**多个专家 Agent**，每个只负责一小块：

```
用户 ──► [Planner] 拆任务 ──► [Researcher] 调研
                                  │
                                  ▼
                            [Writer]        整合成稿
                                  │
                                  ▼
                            [Critic]        评审修改
                                  │
                                  ▼
                              最终报告
```

**核心收益**：

1. **职责清晰**：每个 Agent 一个 system prompt（小而精），上下文纯净；
2. **可独立替换**：调研 Agent 换实现不影响写作 Agent；
3. **可独立评测**：调研准确率、写作品质、Critic 通过率是三个独立指标；
4. **可观察的协作过程**：每条消息归属哪个 Agent、为什么切换、有没有死循环，全部可追溯。

### 17.1.3 多 Agent 框架的三大家

| 框架 | 风格 | 抽象 | 适合 |
| --- | --- | --- | --- |
| **AutoGen**（微软） | 对话驱动（群聊） | `GroupChat` + `Speaker Selection` | 角色扮演、讨论、迭代修订 |
| **LangGraph**（LangChain） | 状态图驱动 | `StateGraph` + `Node/Edge` | 严格流程、确定性工作流 |
| **CrewAI** | 角色任务驱动 | `Crew` + `Task` + `Process` | 轻量快速搭建、流程简单 |

> **本章主讲 AutoGen**——它最擅长"**像开会一样**让一群 Agent 互相启发、互相修改、动态接管"。

---

## 17.2 AutoGen 核心抽象

### 17.2.1 三层架构

AutoGen 0.4+ 重构成清晰的三层：

```
┌─────────────────────────────────────────────────────┐
│  autogen-agentchat   ──►  高层 API（你直接用的）   │
│     AssistantAgent / GroupChat / RoundRobinGroupChat │
└─────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────┐
│  autogen-core        ──►  中层抽象（消息路由/订阅） │
│     RoutedAgent / SingleThreadedAgentRuntime        │
└─────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────┐
│  autogen-ext         ──►  模型/工具/UI 适配层       │
│     OpenAIChatCompletionClient / DockerCommandLine  │
└─────────────────────────────────────────────────────┘
```

> **新手只需 `agentchat` + `ext`**——核心层是给"造框架"的人用的。本章示例全部基于 `agentchat`。

### 17.2.2 `AssistantAgent`：单个"专家"

```python
# 1_single_agent.py —— AutoGen 最简单的单 Agent（对标 OpenAI Agents SDK）
import asyncio
from autogen_agentchat.agents import AssistantAgent
from autogen_agentchat.ui import Console
from autogen_ext.models.openai import OpenAIChatCompletionClient

async def main():
    # 1. 模型客户端（可以是 OpenAI / Azure / Ollama / vLLM 任何兼容端点）
    model_client = OpenAIChatCompletionClient(model="gpt-4o-mini")

    # 2. 定义一个 Agent：姓名 + 系统提示 + 模型
    agent = AssistantAgent(
        name="assistant",                      # 在群聊中显示的名字
        model_client=model_client,
        system_message="你是一个简洁的助手，回答限制在 50 字以内。",
    )

    # 3. 跑一轮
    await Console(agent.run_stream(task="什么是 LangChain？"))

    await model_client.close()

asyncio.run(main())
```

**关键点**：

- `name` 不是装饰——在 `GroupChat` 中是消息的"发件人"，影响调度；
- `system_message` 定义**人设和边界**（"你只能做 X"比"请你尽量做 X"管用）；
- `model_client` 与 Agent 解耦——一个 client 可被多个 Agent 共享。

### 17.2.3 `run_stream` vs `on_messages`：流式与同步

```python
# 同步一次调用
result = await agent.run(task="hello")
print(result.messages[-1].content)

# 流式（边推边看）
async for event in agent.run_stream(task="hello"):
    print(event)   # 包含 ModelRequestStreamingChunk / ToolCallRequestEvent 等
```

---

## 17.3 双 Agent 对话：`Reflection` 模式

最经典的多 Agent 协作：**写 + 评**（Writer + Critic），迭代改进。

### 17.3.1 直接用 `run_stream` 模拟对话

```python
# 2_reflection.py —— Writer / Critic 双 Agent 迭代改进文案
import asyncio
from autogen_agentchat.agents import AssistantAgent
from autogen_agentchat.ui import Console
from autogen_ext.models.openai import OpenAIChatCompletionClient

async def main():
    model = OpenAIChatCompletionClient(model="gpt-4o-mini")

    writer = AssistantAgent(
        name="writer",
        model_client=model,
        system_message=(
            "你是一名营销文案撰稿人。"
            "根据用户的诉求写一段不超过 80 字的中文广告语。"
            "每次都基于上一次反馈修改，否则输出 'DONE'。"
        ),
    )

    critic = AssistantAgent(
        name="critic",
        model_client=model,
        system_message=(
            "你是一名挑剔的文案评审。"
            "按 1~10 分给广告语打分并给 1 条具体修改建议。"
            "如果分数 >= 9，回复 'APPROVED'。"
            "否则只回复'分数:数字 | 建议:...'。"
        ),
    )

    task = "为一款能让人学会 Python 的速溶咖啡写广告语"
    max_rounds = 4

    for i in range(max_rounds):
        # Writer 写
        w = await writer.run(task=task)
        draft = w.messages[-1].content
        print(f"\n[Round {i+1}] Writer: {draft}")

        # Critic 评
        c = await critic.run(task=f"待评文案：{draft}")
        review = c.messages[-1].content
        print(f"[Round {i+1}] Critic: {review}")

        if "APPROVED" in review.upper():
            print("\n✅ 文案通过！")
            break

        # 把反馈喂回 writer
        task = f"上次稿子：{draft}\n评审反馈：{review}\n请基于反馈重写。"

    await model.close()

asyncio.run(main())
```

**运行效果**（示意）：

```
[Round 1] Writer: 速溶咖啡，开启编程新篇章。
[Round 1] Critic: 分数:6 | 建议:缺少"学 Python"的具体利益点...

[Round 2] Writer: 喝一杯代码咖啡，10 分钟写出第一行 Python。
[Round 2] Critic: 分数:8 | 建议:可再口语化一点...

[Round 3] Writer: 一口咖啡，一行代码。今天起，把 Python 泡进杯子。
[Round 3] Critic: APPROVED
✅ 文案通过！
```

### 17.3.2 用 `MaxMessageTermination` 让 Critic 主动叫停

上面用循环计数停，**更好的方式**是给每个 Agent 加终止条件：

```python
from autogen_agentchat.conditions import MaxMessageTermination, TextMentionTermination
from autogen_agentchat.teams import RoundRobinGroupChat

team = RoundRobinGroupChat(
    [writer, critic],
    termination_condition=MaxMessageTermination(6) | TextMentionTermination("APPROVED"),
)
```

> **这是 17.4 的预热**：真正优雅的做法是让 AutoGen 自动调度，而不是我们手写 for 循环。

---

## 17.4 `GroupChat`：让 Agent 真正"开会"

### 17.4.1 为什么用 GroupChat

手写循环的痛点：

- 不知道**下一轮该谁发言**（Writer 改完该让 Critic 评，评完是该让 Writer 再改还是 Planner 介入？）
- 角色多了之后调度逻辑爆炸（5 个专家，谁先谁后？）
- 终止条件需要手写（"达到分数"、"超过 N 轮"、"无异议"）

`GroupChat` 就是 AutoGen 给出的答案：**一个共享对话，多个 Agent，按规则轮流/被选中发言**。

### 17.4.2 `RoundRobinGroupChat`：最简的轮询

```python
# 3_round_robin.py —— 三 Agent 轮询：Researcher → Writer → Critic 循环
from autogen_agentchat.agents import AssistantAgent
from autogen_agentchat.teams import RoundRobinGroupChat
from autogen_agentchat.conditions import MaxMessageTermination, TextMentionTermination
from autogen_ext.models.openai import OpenAIChatCompletionClient
import asyncio

async def main():
    model = OpenAIChatCompletionClient(model="gpt-4o-mini")

    researcher = AssistantAgent(
        name="researcher",
        model_client=model,
        system_message=(
            "你是调研员。用户提供主题后，你只用一句话给出 3 个最关键的调研点。"
            "完成后回复 'RESEARCH_DONE'。"
        ),
    )
    writer = AssistantAgent(
        name="writer",
        model_client=model,
        system_message=(
            "你是撰稿人。基于调研点写 100 字以内的短报告。"
            "完成后回复 'DRAFT_DONE'。"
        ),
    )
    critic = AssistantAgent(
        name="critic",
        model_client=model,
        system_message=(
            "你是评审。给短报告打分（1~10），并给一条修改建议。"
            "如果分数 >= 9，回复 'APPROVED'。"
            "否则回复'分数:N | 建议:...'。"
        ),
    )

    team = RoundRobinGroupChat(
        participants=[researcher, writer, critic],
        termination_condition=MaxMessageTermination(12) | TextMentionTermination("APPROVED"),
    )

    result = await team.run(task="主题：2026 年 AI Agent 框架的三大趋势")
    for msg in result.messages:
        print(f"[{msg.source}] {msg.content}\n")

    await model.close()

asyncio.run(main())
```

**核心机制**：

- **轮询顺序**：`participants` 列表决定发言顺序，到末尾回头循环；
- **终止条件**：用 `|` 组合多个条件——只要满足任一就停；
- **消息流**：所有 Agent 共享同一份消息历史，但每条消息有 `source` 字段标识发言者。

### 17.4.3 `SelectorGroupChat`：让 LLM 当"主持人"

轮询的局限：**僵化**。有时 Critic 想直接反问 Researcher 重新调研（跳过 Writer），但轮询机制下必须先轮到 Writer。

`SelectorGroupChat` 让一个**selector**（默认也是 LLM）动态决定"下一个该谁发言"。

```python
# 4_selector.py —— 让 LLM 选下一个发言人
from autogen_agentchat.teams import SelectorGroupChat

team = SelectorGroupChat(
    participants=[researcher, writer, critic],
    model_client=model,                                   # ← 用 LLM 选下一个发言人
    termination_condition=MaxMessageTermination(12) | TextMentionTermination("APPROVED"),
    selector_prompt=(
        "根据当前对话状态，选下一个最合适的 Agent 发言：\n"
        "  - 如果需要补充调研信息 → researcher\n"
        "  - 如果需要基于调研写报告 → writer\n"
        "  - 如果需要评审打分 → critic\n"
        "只返回 Agent 名字，不要解释。"
    ),
)
```

**Selector 工作原理**：

1. 把当前消息历史 + 参与者名单 + selector_prompt 喂给 LLM；
2. LLM 返回一个 Agent 名字；
3. 下一个轮到这个 Agent 发言。

**进阶玩法**：用函数式 selector（完全可控）：

```python
def custom_selector(messages):
    last = messages[-1].content
    if "分数:" in last and int(last.split("分数:")[1].split("|")[0]) < 7:
        return "writer"               # 分数低，让 writer 直接重写，不让 critic 再评
    if "RESEARCH_DONE" in last:
        return "writer"
    if "DRAFT_DONE" in last:
        return "critic"
    return "researcher"               # 默认从 researcher 开始

team = SelectorGroupChat(
    participants=[researcher, writer, critic],
    selector_func=custom_selector,    # ← 不依赖 LLM，可观测可调试
)
```

> **对照 Java**：`SelectorGroupChat` ≈ 工作流引擎里的"网关节点"（Gateway / Exclusive Gateway），由表达式或规则决定下一步走向。

---

## 17.5 工具调用：`FunctionTool` 与 MCP 整合

### 17.5.1 给 Agent 装工具

```python
# 5_tools.py —— 给 Researcher Agent 装"联网搜索"工具
import asyncio, json
from autogen_agentchat.agents import AssistantAgent
from autogen_core.tools import FunctionTool

def search_web(query: str) -> str:
    """搜索网络，返回 3 条最相关结果的摘要"""
    # 真实场景调 Bing/Google/Tavily；这里 mock
    return json.dumps({
        "results": [
            {"title": f"关于 {query} 的最新进展", "snippet": "AutoGen 0.4 引入了分层架构..."},
            {"title": f"{query} 在生产中的应用", "snippet": "多家企业已用其构建多 Agent 系统..."},
        ]
    }, ensure_ascii=False)

search_tool = FunctionTool(
    search_web,
    description="当用户询问最新事实/数据时调用。输入关键词，返回 JSON。"
)

researcher = AssistantAgent(
    name="researcher",
    model_client=model,
    tools=[search_tool],
    system_message="你是调研员，需要查实时数据就调 search_web。",
)
```

**关键**：工具函数的 `docstring` + `type hints` **会被 LLM 看到**，决定它能不能调对工具。

### 17.5.2 整合第 13 章的 MCP 工具

```python
# 6_mcp_tool.py —— 把 MCP Server 的工具装进 AutoGen Agent
from autogen_ext.tools.mcp import McpWorkbench, StreamableHttpServerParams

mcp = McpWorkbench(
    server_params=StreamableHttpServerParams(url="http://localhost:8001/mcp")
)

agent = AssistantAgent(
    name="data_agent",
    model_client=model,
    workbench=mcp,        # ← workbench 与 tools 互斥：要么直接给 tools，要么接 MCP server
    system_message="你有 mcp server 上的全部工具，按需调用。",
)
```

> **注意**：`tools` 与 `workbench` 二选一。前者适合自写工具，后者适合复用 MCP 生态。

---

## 17.6 实战：多 Agent 市场调研小组

### 17.6.1 项目目标

输入一个调研主题（如"2026 年 RAG 技术趋势"），输出**一份带分工的完整报告**：

- **Planner**：拆任务、定大纲；
- **Researcher×3**：并发调研 3 个子主题；
- **Writer**：合并调研结果写成报告；
- **Critic**：评审并打分，未达标打回重写。

### 17.6.2 完整工程

```python
# market_research_team.py —— 多 Agent 调研小组
import asyncio, os
from autogen_agentchat.agents import AssistantAgent
from autogen_agentchat.teams import SelectorGroupChat, RoundRobinGroupChat
from autogen_agentchat.conditions import MaxMessageTermination, TextMentionTermination
from autogen_agentchat.ui import Console
from autogen_core.tools import FunctionTool
from autogen_ext.models.openai import OpenAIChatCompletionClient

# 0. mock 搜索工具（真实场景换成 Tavily/SerpAPI）
async def search_web_async(query: str) -> str:
    return f"[Mock] 关于 '{query}' 的 3 条最新结果：\n1. ...\n2. ...\n3. ..."

search_tool = FunctionTool(
    search_web_async,
    description="联网搜索最新事实。",
)

async def main():
    model = OpenAIChatCompletionClient(model="gpt-4o")

    # ---- 角色定义 ----
    planner = AssistantAgent(
        name="planner",
        model_client=model,
        system_message=(
            "你是调研组长。用户给主题后，你把任务拆成 3 个独立子主题。"
            "格式：`子主题1:xxx | 子主题2:xxx | 子主题3:xxx`，然后回复 'PLAN_DONE'。"
        ),
    )

    researcher = AssistantAgent(
        name="researcher",
        model_client=model,
        tools=[search_tool],
        system_message=(
            "你是调研员。基于当前对话中的子主题调 search_web 调研，"
            "完成后回复 'RESEARCH_DONE: <本子主题 100 字摘要>'。"
        ),
    )

    writer = AssistantAgent(
        name="writer",
        model_client=model,
        system_message=(
            "你是撰稿人。把所有 RESEARCH_DONE 的内容整合成一篇 500 字的结构化报告，"
            "完成后回复 'DRAFT_DONE'。"
        ),
    )

    critic = AssistantAgent(
        name="critic",
        model_client=model,
        system_message=(
            "你是评审。给报告打 1~10 分并给一条建议。"
            ">=9 回复 'APPROVED'；否则 '分数:N | 建议:...'。"
        ),
    )

    # ---- 编排：两阶段 ----
    # 阶段 1: Planner → Researcher×3（轮询 3 次，强制收集 3 份调研）
    phase1 = RoundRobinGroupChat(
        participants=[planner, researcher, researcher, researcher],
        termination_condition=TextMentionTermination("RESEARCH_DONE"),
    )

    # 阶段 2: Writer ↔ Critic（用 SelectorGroupChat 让 Critic 可决定是否打回）
    phase2 = SelectorGroupChat(
        participants=[writer, critic],
        model_client=model,
        termination_condition=MaxMessageTermination(8) | TextMentionTermination("APPROVED"),
    )

    # ---- 跑流程 ----
    print("=" * 60)
    print("📋 Phase 1: 任务拆分 + 调研")
    print("=" * 60)
    p1_result = await phase1.run(task="调研主题：2026 年 RAG 技术的主要趋势")
    for msg in p1_result.messages:
        print(f"[{msg.source}] {msg.content[:200]}\n")

    print("=" * 60)
    print("✍️ Phase 2: 撰写 + 评审")
    print("=" * 60)
    # 把 Phase 1 的输出作为 Phase 2 的输入
    phase2_input = "\n".join(
        f"[{m.source}] {m.content}" for m in p1_result.messages
    )
    p2_result = await phase2.run(
        task=f"以下是前期调研：\n{phase2_input}\n\n请整合成报告。"
    )
    for msg in p2_result.messages:
        print(f"[{msg.source}] {msg.content[:200]}\n")

    await model.close()

asyncio.run(main())
```

### 17.6.3 进阶：用 `DiGraphBuilder` 画更复杂的拓扑

`RoundRobinGroupChat` 和 `SelectorGroupChat` 是"线/星"形结构，要"任意拓扑"用：

```python
# 7_digraph.py —— 显式拓扑：Planner → 3 个 Researcher 并行 → Writer → Critic
from autogen_agentchat.teams import DiGraphBuilder, GraphFlow

builder = DiGraphBuilder()
p = builder.add_node(planner)
r1 = builder.add_node(researcher_1)
r2 = builder.add_node(researcher_2)
r3 = builder.add_node(researcher_3)
w = builder.add_node(writer)
c = builder.add_node(critic)

# 拓扑
builder.add_edge(p, r1)
builder.add_edge(p, r2)
builder.add_edge(p, r3)
builder.add_edge(r1, w)
builder.add_edge(r2, w)
builder.add_edge(r3, w)
builder.add_edge(w, c)
builder.add_edge(c, w)        # 评审不通过 → 退回 writer 重写（循环）

team = GraphFlow(
    participants=builder.build(),
    termination_condition=MaxMessageTermination(15) | TextMentionTermination("APPROVED"),
)
```

> 这等于**用图结构表达工作流**，与第 12 章 LangGraph 的 `StateGraph` 异曲同工。

---

## 17.7 人机协同：`UserProxyAgent` 与 `Handoff`

### 17.7.1 让"人"加入群聊

某些关键决策需要人工介入（如"是否批准发布"），AutoGen 提供 `UserProxyAgent`：

```python
from autogen_agentchat.agents import UserProxyAgent

user = UserProxyAgent(
    name="human",
    input_func=input,          # 控制台输入（真实场景可换成 Web 回调/Slack 消息）
)

team = SelectorGroupChat(
    participants=[assistant, user],
    model_client=model,
)
```

运行时会停在 `UserProxyAgent` 等待输入。

### 17.7.2 `Handoff`：把任务"扔"给另一个 Agent

```python
from autogen_agentchat.agents import AssistantAgent

triage = AssistantAgent(
    name="triage",
    model_client=model,
    system_message=(
        "你是前台。根据用户问题决定交给哪个专家：\n"
        "  - 技术问题 → tech_expert\n"
        "  - 商务问题 → biz_expert\n"
        "  - 其他问题 → 直接回复'无法处理'\n"
        "用 `handoff_to=<agent_name>` 移交。"
    ),
)

tech_expert = AssistantAgent(name="tech_expert", model_client=model, ...)
biz_expert  = AssistantAgent(name="biz_expert",  model_client=model, ...)
```

`Handoff` 让 Agent 在一轮内把控制权交给另一个 Agent，相当于"转人工"。

---

## 17.8 观测与调试

### 17.8.1 `Console` UI 与 `run_stream`

```python
from autogen_agentchat.ui import Console

await Console(team.run_stream(task="..."))
```

会实时打印每条消息，类似 OpenAI Agents SDK 的 Trace。

### 17.8.2 `Tracing`：把所有事件写入 JSONL

```python
from autogen_core import SingleThreadedAgentRuntime
from autogen_core.tracing import instrument_autogen

# 开启 OpenTelemetry 兼容 trace
result = await instrument_autogen(team.run_stream(task="..."))
# 或自己订阅消息事件
async for event in team.run_stream(task="..."):
    print(event)   # TaskStarted / MessageAdded / ToolCallExecuted / TeamFinished 等
```

**事件类型**（按时间序）：

```
TaskStarted
MessageAdded(source=user, content=...)
MessageAdded(source=planner, content=...)
ToolCallRequested(source=researcher, tool=search_web)
ToolCallExecuted(source=researcher, result=...)
MessageAdded(source=researcher, content=...)
...
TeamFinished(reason=TextMentionTermination, message=...)
```

> **对照 Java**：这套事件 ≈ Akka Actor 的事件总线 / Spring 的 ApplicationEvent。可以接入 ELK / Langfuse / Phoenix 做可视化追踪。

---

## 17.9 AutoGen vs LangGraph：怎么选？

| 维度 | AutoGen | LangGraph |
| --- | --- | --- |
| **抽象** | 群聊 + Speaker Selection | 状态图 + Node/Edge |
| **典型场景** | 多专家讨论、迭代修订、角色扮演 | 严格流程、确定性流水线、人工审批 |
| **可控性** | 中（默认让 LLM 选发言人） | 高（图是显式声明的） |
| **可观测性** | 强（每条消息归属 Agent，事件流完整） | 中（依赖 Tracing） |
| **学习曲线** | 低（10 行就能跑双 Agent） | 中（要先理解 StateGraph） |
| **生态** | MCP / OpenAI / Azure / 本地模型 | LangChain 全家桶 + LangSmith |
| **何时选** | "**让 Agent 自己开会**" | "**我画好流程，Agent 跑节点**" |

**经验法则**：

- 3~5 个角色、需要互相启发/质疑/修改 → **AutoGen**；
- 流程严格、要可审计/可解释 → **LangGraph**；
- 拿不准 → 先用 AutoGen 跑通 MVP，再决定要不要重构到 LangGraph。

---

## 17.10 常见踩坑

| 坑 | 症状 | 解决 |
| --- | --- | --- |
| **Agent 名字冲突** | `RuntimeError: Duplicate agent name` | `name` 全局唯一，用 `module.role` 命名风格 |
| **群聊死循环** | Writer ↔ Critic 无限来回 | 加 `MaxMessageTermination` 硬上限 |
| **Selector 选错人** | LLM 选了一个不存在的 Agent | selector_prompt 加约束 + 用 `selector_func` 兜底 |
| **工具参数类型错误** | LLM 调工具时传错类型 | 工具函数加严格 type hints + 用 `pydantic` 校验 |
| **群聊上下文爆炸** | 几轮后消息列表超长，token 爆掉 | 用 `summary_method` 在每轮做摘要压缩 |
| **终止条件永不触发** | `TextMentionTermination("APPROVED")` 不生效 | 检查 Agent 的 system_message 是否真的会在达标时输出 `APPROVED` |
| **Persona 串味** | Researcher 越界去写报告 | system_message 显式约束："你**只**做调研，禁止输出报告。" |

---

## 17.11 面试高频问题

**Q1. AutoGen 的核心抽象是什么？**

A：`AssistantAgent`（单 Agent）+ `GroupChat`（群聊容器）+ Speaker Selection（决定下一轮谁发言）+ Termination Condition（停止条件）。其余都是这三者的组合与扩展。

**Q2. `RoundRobinGroupChat` 和 `SelectorGroupChat` 的区别？**

A：轮询按固定顺序；Selector 让 LLM（或自定义函数）选下一个发言人。Selector 灵活但不可预测；轮询可控但僵化。

**Q3. 如何让 AutoGen 跑得"省 token"？**

A：
1. 控制群聊轮数（`MaxMessageTermination`）；
2. 在系统提示里禁止"客套话"（"不要寒暄，直接回答"）；
3. 给每个 Agent 一个**窄而深**的 system_message（不要塞 2000 字规则）；
4. 用便宜模型做"打下手"的 Agent（Researcher 用 mini，Writer 用 4o）。

**Q4. AutoGen 怎么调试？**

A：`Console` UI 看实时消息流 + `run_stream` 输出全部事件 + 关键节点用 mock 工具隔离 LLM 干扰（先验证流程逻辑，再接真实 LLM）。

**Q5. AutoGen vs CrewAI 怎么选？**

A：AutoGen 更底层、更灵活、微软官方维护；CrewAI 上手更快、抽象更"高"（`Crew` + `Task` + `Process` 三件套）、社区更活跃。简单场景 CrewAI，重场景 AutoGen。

---

## 17.12 本章小结

| 你学到了 | 对应技能点 |
| --- | --- |
| AutoGen 三层架构 | agentchat / core / ext 的边界 |
| `AssistantAgent` 单 Agent | `system_message` 设计、`model_client` 复用 |
| `RoundRobinGroupChat` / `SelectorGroupChat` | 群聊编排 + Speaker Selection |
| `FunctionTool` / MCP 整合 | 让 Agent 拥有工具能力 |
| `DiGraphBuilder` / `GraphFlow` | 显式拓扑建模 |
| `UserProxyAgent` / Handoff | 人机协同 |
| 观测 + 终止条件 | 可控可调试的多 Agent 系统 |
| AutoGen vs LangGraph 选型 | 实战决策能力 |

**下一章预告**：第 18 章把视角从"业务 Agent"转向"**为 Agent 而写的测试与 Harness**"——如何用 Python 写测试 Harness（pytest + 覆盖率 + mutation）以及 AI Agent Harness（端到端质量门禁 + LLM-as-Judge + CI 流水线），构建生产可上线的多 Agent 系统。