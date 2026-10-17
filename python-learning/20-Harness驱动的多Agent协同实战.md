# 第二十章 Harness 驱动的多 Agent 协同：从"能跑"到"敢上线"

> 本章目标：第 17 章学会了用 AutoGen 编排多 Agent，第 18 章学会了用 Harness 做质量护栏。本章把两者**合起来**——回答两个工程核心问题：
> 1. **多 Agent 到底怎么编写？** 从任务拆解 → 角色定义 → 通信协议 → 编排实现 → 终止兜底的完整五步法，先**手写最小框架**理解原理，再用 AutoGen 落地；
> 2. **Harness 怎么保障多 Agent？** 建立**三层门禁**（单 Agent 级 / 协同级 / 端到端级），覆盖多 Agent 特有的失效模式（错误传播、角色越界、死循环、成本叠加）。
>
> 最后交付一个**可运行的「多 Agent 代码评审系统」+ 完整 Harness**（Python 版，对标 java-learning 第 85 章 Spring AI 版）。
>
> 前置知识：第 9 章（pytest）、第 12 章（LangGraph）、第 16 章（Agent 评测）、第 17 章（AutoGen）、第 18 章（Python Harness）。对标 Java：Akka Actor 集群 + JUnit 集成测试 + ArchUnit 架构守护。
>
> 环境准备：`pip install "autogen-agentchat[openai]" pytest pytest-cov pytest-asyncio vcrpy respx deepeval`，设置 `OPENAI_API_KEY`。

---

## 20.1 为什么多 Agent 更需要 Harness

### 20.1.1 多 Agent 放大了所有问题

单 Agent 出错只是"答错一次"；多 Agent 出错会**传播、放大、甚至失控**：

| 失效模式 | 单 Agent | 多 Agent（放大后） |
| --- | --- | --- |
| **错误传播** | 答错，用户重试 | Agent A 的错答案 → Agent B 基于错信息继续 → 全链路污染 |
| **角色越界** | 不存在 | Researcher 越界去写报告，Writer 越界调工具 |
| **死循环** | 不存在 | Writer ↔ Critic 无限互改，token 烧穿 |
| **成本叠加** | 1 次 LLM 调用 | N 个 Agent × M 轮 × 每次全量上下文 |
| **交接丢失** | 不存在 | Handoff 时参数没传全，下游 Agent 抓瞎 |
| **静默降级** | 答非所问 | 某个 Agent 悄悄失败，但流程"看起来跑完了" |

> **核心洞察**：多 Agent 系统的复杂度是**乘法的**——5 个 Agent 两两交互就有 10 条边。没有 Harness，你根本不知道哪条边出了问题。

### 20.1.2 单 Agent 测试 vs 多 Agent 协同测试

| 维度 | 单 Agent 测试 | 多 Agent 协同测试 |
| --- | --- | --- |
| 被测对象 | 一个 Agent 的输入→输出 | 一组 Agent 的**交互图** |
| 断言重点 | 内容正确、工具调对 | + **路由正确**（该谁发言）、**交接完整**、**无环** |
| 失败定位 | 看这个 Agent 的 trace | 要看**整条消息链路**，找第一个出错的 Agent |
| 成本 | 低 | 高（N×M 倍 LLM 调用） |
| 稳定性 | 中 | 低（组合爆炸） |

### 20.1.3 Harness 的三层门禁（本章主线）

```
┌─────────────────────────────────────────────┐
│  L3 端到端级：最终质量 / 成本 / 延迟         │  ← 慢，每天跑
├─────────────────────────────────────────────┤
│  L2 协同级：路由 / 交接 / 终止 / 无环        │  ← 中速，PR 必跑
├─────────────────────────────────────────────┤
│  L1 单 Agent 级：每个 Agent 独立评测         │  ← 快，每次提交跑
└─────────────────────────────────────────────┘
```

**关键原则**：**能隔离就隔离**。L1 把每个 Agent 单独 mock 掉其他 Agent 来测；只有 L1 全绿才进 L2；L2 全绿才进 L3（最贵）。

---

## 20.2 多 Agent 的四种协作拓扑

写多 Agent 之前，先选**拓扑**（谁跟谁说话）。这是架构决策，选错了后面全白搭。

### 20.2.1 拓扑一：流水线（Pipeline / Assembly Line）

```
输入 ──► [A] ──► [B] ──► [C] ──► 输出
```

- **特点**：单向、无反馈、每个 Agent 只做一件事；
- **适合**：确定性流程（ETL、文档处理链、翻译→润色→排版）；
- **优点**：简单、可预测、易测试（每个节点独立）；
- **缺点**：无迭代，一步错全链错；
- **实现**：LangGraph `StateGraph` 线性连边 / AutoGen `RoundRobinGroupChat` 跑一轮。

### 20.2.2 拓扑二：主从编排（Orchestrator-Worker / Supervisor）

```
              ┌──► [W1 搜索]
[Supervisor] ─┼──► [W2 计算] ──► 汇总回 Supervisor
              └──► [W3 写作]
```

- **特点**：一个"大脑"分配任务给多个"手脚"，Worker 之间**不互相说话**；
- **适合**：任务可并行拆分（调研 3 个子主题、同时审 5 个文件）；
- **优点**：并行快、职责清晰、易扩展（加 Worker 不改逻辑）；
- **缺点**：Supervisor 是单点瓶颈 + 单点故障；
- **实现**：LangGraph `Send` API / AutoGen `DiGraphBuilder`（Supervisor 扇出）。

### 20.2.3 拓扑三：圆桌群聊（Group Chat / Round Table）

```
   [A] ←────→ [B]
    ↑  ╲    ╱  ↑
    │   ╲  ╱   │
    └────[C]───┘
   （共享消息历史，动态决定谁发言）
```

- **特点**：所有人共享一份对话，动态决定下一个发言人；
- **适合**：需要**讨论、质疑、迭代修订**（写作+评审、多专家会诊）；
- **优点**：能产生"1+1>2"的涌现效果；
- **缺点**：最难控制（可能死循环、跑偏）；
- **实现**：AutoGen `SelectorGroupChat` / `RoundRobinGroupChat`。

### 20.2.4 拓扑四：对等移交（Peer-to-Peer / Handoff）

```
[前台 Triage] ──handoff──► [技术专家]
       │
       └──handoff────────► [商务专家]
```

- **特点**：Agent 之间**互相转交控制权**，一次只有一个 Agent 活跃；
- **适合**：路由分发、客服转接、意图分流；
- **优点**：上下文干净（只有一个 Agent 在对话）；
- **缺点**：移交时容易丢信息；
- **实现**：OpenAI Agents SDK `handoffs` / AutoGen `Handoff`。

### 20.2.5 选型决策树

```
你的多 Agent 场景是？
│
├─ 任务能拆成"独立子任务"，且互不依赖？
│   └─ 是 → 【主从编排 Supervisor-Worker】（可并行）
│
├─ 流程是"固定几步"，每步输入输出明确？
│   └─ 是 → 【流水线 Pipeline】
│
├─ 需要"多个专家互相质疑、迭代改进"？
│   └─ 是 → 【圆桌群聊 Group Chat】⭐ 最灵活也最难控
│
├─ 只是"按意图分发给不同专家"？
│   └─ 是 → 【对等移交 Handoff】
│
└─ 拿不准 → 先用【流水线】跑通，再逐步加复杂度
```

> **经验法则**：**拓扑越简单越好**。能用流水线别用群聊——群聊的调试成本是流水线的 5 倍。

---

## 20.3 多 Agent 编写五步法

不管用什么框架，写多 Agent 都走这五步：

```
① 任务拆解 ──► ② 角色定义 ──► ③ 通信协议 ──► ④ 编排实现 ──► ⑤ 终止兜底
```

### 20.3.1 步骤一：任务拆解

**原则**：拆到"**每个子任务能被一个清晰的 system prompt 描述**"为止。

以"代码评审"为例：

```
❌ 拆得太粗：
   [评审 Agent] "审这段代码"           ← prompt 要写 2000 字，注意力分散

✅ 拆得合适：
   [安全评审] "找出 SQL 注入、硬编码密钥、越权"         ← 50 字说清
   [性能评审] "找出 N+1 查询、循环内 IO、内存泄漏"      ← 50 字说清
   [风格评审] "检查命名、重复代码、函数长度"            ← 50 字说清
   [测试评审] "检查测试覆盖、边界用例"                  ← 50 字说清
   [聚合器]   "合并 4 份意见，去重、定级、排序"         ← 50 字说清
```

**判断标准**：如果一个 Agent 的 system prompt 超过 **200 字**，说明还能拆。

### 20.3.2 步骤二：角色定义（system prompt 设计）

每个 Agent 的 prompt 必须包含**四要素**：

```python
system_message = """
【角色】你是一名 {角色名}。                    ← ① 身份
【职责】你**只**负责 {具体任务}，禁止做 {越界行为}。  ← ② 边界（关键！）
【输入】你会收到 {输入格式}。                   ← ③ 契约
【输出】你必须输出 {输出格式}。完成后以 {结束标记} 结尾。 ← ④ 契约 + 终止信号
"""
```

**四个要素缺一不可**，其中 **②边界** 最容易被忽略，也最容易出问题：

```python
# ❌ 没有边界约束
system_message = "你是调研员，负责调研。"

# ✅ 有明确边界
system_message = """
你是调研员。
【职责】你**只**负责调用 search_web 搜集事实数据，并输出 100 字摘要。
【禁止】禁止撰写报告、禁止评价他人工作、禁止给出建议。
        （撰写由 writer 负责，评价由 critic 负责）
【输出】摘要开头必须是 `RESEARCH_DONE: `。
"""
```

> **踩坑提醒**：角色越界是多 Agent 第一大 bug。解决办法就是 prompt 里**显式写出"谁负责什么，你不负责什么"**。

### 20.3.3 步骤三：通信协议（消息契约）

定义 Agent 之间"说什么、什么格式"。这是**多 Agent 的 API 设计**。

```python
# 用 Pydantic 定义消息契约（强烈推荐，可校验）
from pydantic import BaseModel, Field
from typing import Literal

class ReviewFinding(BaseModel):
    """单条评审意见 —— 专家 → 聚合器的契约"""
    agent: Literal["security", "performance", "style", "test"]
    severity: Literal["BLOCKER", "MAJOR", "MINOR", "INFO"]
    file: str
    line: int | None
    message: str = Field(max_length=200)
    suggestion: str | None = None

class ReviewReport(BaseModel):
    """聚合结果 —— 聚合器 → 用户的契约"""
    findings: list[ReviewFinding]
    total: int
    verdict: Literal["APPROVE", "REQUEST_CHANGES"]
    summary: str = Field(max_length=300)
```

**为什么用 Pydantic**：

1. **强制结构**：LLM 输出不符合 schema 会报错，不会静默出错；
2. **可断言**：Harness 里能精确断言"必须包含 BLOCKER 级别的 finding"；
3. **自文档**：契约即文档，新加 Agent 时照着写就行。

### 20.3.4 步骤四：编排实现

三个层次，从底层到框架：

| 层次 | 做法 | 适合 |
| --- | --- | --- |
| **手写** | 自己写消息总线 + 调度循环 | 理解原理、极简场景 |
| **图编排** | LangGraph `StateGraph` / AutoGen `DiGraphBuilder` | 拓扑明确、需要可解释 |
| **群聊** | AutoGen `GroupChat` + Speaker Selection | 动态讨论 |

**20.4 节先手写，20.5 节用框架。**

### 20.3.5 步骤五：终止与兜底（**最容易被忽略**）

多 Agent 必须有**三层保险**，否则迟早死循环烧穿预算：

```python
# 保险 1：硬轮数上限（必加）
termination = MaxMessageTermination(20)

# 保险 2：显式终止词（Agent 主动报告完成）
termination |= TextMentionTermination("ALL_DONE")

# 保险 3：超时熔断（框架层的 asyncio.wait_for）
result = await asyncio.wait_for(team.run(task=...), timeout=120)

# 保险 4：成本熔断（自定义，超预算直接停）
if total_cost > budget:
    break

# 保险 5：人工兜底（关键决策交给 UserProxyAgent）
```

> **铁律**：**任何多 Agent 系统都必须有硬轮数上限 + 超时**。没有这两条就等于"允许无限烧钱"。

---

## 20.4 手写最小多 Agent 框架（理解原理）

先用 **100 行**手写一个最小多 Agent 框架，理解"消息总线 + 调度"的本质。

### 20.4.1 核心抽象

```python
# mini_multiagent.py —— 手写最小多 Agent 框架（教学用，可直接运行）
import asyncio
from dataclasses import dataclass, field
from typing import Callable, Awaitable
from openai import AsyncOpenAI

client = AsyncOpenAI()

# ============ ① 消息 ============
@dataclass
class Message:
    source: str          # 谁发的
    content: str         # 内容
    to: str | None = None  # 发给谁（None = 广播）

# ============ ② Agent 基类 ============
@dataclass
class Agent:
    name: str
    system: str
    model: str = "gpt-4o-mini"

    async def act(self, history: list[Message]) -> Message:
        """读历史 → 输出一条消息"""
        messages = [
            {"role": "system", "content": self.system},
            *[
                {"role": "assistant" if m.source == self.name else "user",
                 "content": f"[{m.source}] {m.content}"}
                for m in history
            ],
        ]
        r = await client.chat.completions.create(model=self.model, messages=messages)
        return Message(source=self.name, content=r.choices[0].message.content)

# ============ ③ 调度器（最简：轮询）============
async def run_round_robin(agents: list[Agent], task: str, max_rounds: int = 6) -> list[Message]:
    history = [Message(source="user", content=task)]
    for _ in range(max_rounds):
        for agent in agents:
            msg = await agent.act(history)
            history.append(msg)
            print(f"[{msg.source}] {msg.content[:120]}")
            # 终止条件：任何人喊 DONE
            if "ALL_DONE" in msg.content.upper():
                return history
    return history
```

### 20.4.2 跑起来：Writer + Critic 双 Agent

```python
# 1_mini_demo.py
import asyncio
from mini_multiagent import Agent, run_round_robin

async def main():
    writer = Agent(
        name="writer",
        system="你是文案撰稿人。写 50 字以内广告语。满意就回复 'ALL_DONE'。",
    )
    critic = Agent(
        name="critic",
        system="你是评审。打分 1~10 并给一条建议。分数>=9 回复 'ALL_DONE'。",
    )
    history = await run_round_robin([writer, critic], task="为 Python 速溶咖啡写广告语")
    print("\n=== 最终对话 ===")
    for m in history:
        print(f"[{m.source}] {m.content}")

asyncio.run(main())
```

### 20.4.3 升级：加"动态选人"（Selector）

轮询太死板，让 LLM 选下一个发言人：

```python
# ============ ④ 动态调度器 ============
async def select_next(agents: list[Agent], history: list[Message]) -> Agent:
    names = [a.name for a in agents]
    prompt = f"""当前对话：
{chr(10).join(f"[{m.source}] {m.content}" for m in history[-6:])}

可选 Agent: {names}
谁下一个发言最合适？只返回名字，不要解释。"""
    r = await client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[{"role": "user", "content": prompt}],
    )
    chosen = r.choices[0].message.content.strip()
    return next((a for a in agents if a.name in chosen), agents[0])

async def run_dynamic(agents: list[Agent], task: str, max_rounds: int = 8) -> list[Message]:
    history = [Message(source="user", content=task)]
    for _ in range(max_rounds):
        agent = await select_next(agents, history)
        msg = await agent.act(history)
        history.append(msg)
        print(f"[{msg.source}] {msg.content[:120]}")
        if "ALL_DONE" in msg.content.upper():
            break
    return history
```

> **这就是 AutoGen `SelectorGroupChat` 的本质**。手写一遍，框架的魔法就消失了。

---

## 20.5 用 AutoGen 实现（框架版）

理解了原理，用框架就很快。承接第 17 章。

### 20.5.1 四专家 + 聚合器的代码评审系统

```python
# review_team.py —— 多 Agent 代码评审（AutoGen 版）
import asyncio, os
from autogen_agentchat.agents import AssistantAgent
from autogen_agentchat.teams import RoundRobinGroupChat
from autogen_agentchat.conditions import MaxMessageTermination
from autogen_ext.models.openai import OpenAIChatCompletionClient

REVIEW_DIMENSIONS = {
    "security": ("安全评审", "SQL 注入、硬编码密钥、越权、XSS、不安全的反序列化"),
    "performance": ("性能评审", "N+1 查询、循环内 IO/HTTP、内存泄漏、重复计算"),
    "style": ("风格评审", "命名不规范、重复代码、函数过长、魔法数字"),
    "test": ("测试评审", "缺少测试、边界用例缺失、断言过弱"),
}

def build_reviewers(model_client) -> list[AssistantAgent]:
    agents = []
    for key, (role, focus) in REVIEW_DIMENSIONS.items():
        agents.append(AssistantAgent(
            name=key,
            model_client=model_client,
            system_message=f"""
你是{role}。
【职责】你**只**审查代码的 {focus} 问题。
【禁止】禁止审查其他维度（其他专家负责），禁止修改代码，禁止输出总结报告。
【输出格式】每条问题一行：`LINE:<行号> | SEVERITY:<BLOCKER|MAJOR|MINOR> | <一句话问题>`
【无问题】输出 `NO_ISSUE`
""",
        ))
    # 聚合器
    agents.append(AssistantAgent(
        name="aggregator",
        model_client=model_client,
        system_message="""
你是评审聚合器。
【职责】合并前面 4 位专家的意见：去重、按严重度排序、给出最终结论。
【输出】结论必须是 `VERDICT: APPROVE` 或 `VERDICT: REQUEST_CHANGES`，后跟 3 行以内摘要。
【结束】输出完结论后回复 `ALL_DONE`。
""",
    ))
    return agents

async def review_code(code: str, language: str = "python") -> str:
    model_client = OpenAIChatCompletionClient(model="gpt-4o")
    agents = build_reviewers(model_client)

    team = RoundRobinGroupChat(
        participants=agents,
        termination_condition=MaxMessageTermination(len(agents)),  # 每人一次，硬上限
    )

    result = await team.run(
        task=f"请评审以下 {language} 代码：\n\n```{language}\n{code}\n```"
    )
    await model_client.close()

    # 取聚合器的最后一条消息
    agg_msgs = [m for m in result.messages if m.source == "aggregator"]
    return agg_msgs[-1].content if agg_msgs else "无聚合结果"

# 使用
if __name__ == "__main__":
    code = open("target_code.py").read()
    print(asyncio.run(review_code(code)))
```

### 20.5.2 关键设计点回顾

| 五步法 | 本例的实现 |
| --- | --- |
| ① 任务拆解 | 安全/性能/风格/测试 + 聚合，5 个 Agent |
| ② 角色定义 | 每个 prompt 含【职责】【禁止】【输出格式】三要素 |
| ③ 通信协议 | 输出格式 `LINE:x | SEVERITY:y | msg`，聚合器能解析 |
| ④ 编排实现 | `RoundRobinGroupChat`（因为是"每人审一次"的确定性流程） |
| ⑤ 终止兜底 | `MaxMessageTermination(5)` 硬上限（5 个 Agent 各一次） |

---

## 20.6 Harness 三层门禁：保障多 Agent

现在给上面的评审系统装 Harness。

### 20.6.1 L1 单 Agent 级：隔离测试每个 Agent

**思路**：mock 掉其他 Agent，只测一个 Agent 的"输入→输出"是否符合契约。

```python
# tests/l1/test_single_agent.py
import pytest, asyncio
from review_team import build_reviewers
from autogen_ext.models.openai import OpenAIChatCompletionClient

# 用 respx mock LLM，让测试不花钱也不慢
import respx
from httpx import Response

@respx.mock
@pytest.mark.asyncio
async def test_security_agent_detects_sql_injection():
    # mock LLM 返回：安全专家发现了 SQL 注入
    respx.post("https://api.openai.com/v1/chat/completions").mock(
        return_value=Response(200, json={
            "choices": [{"message": {"role": "assistant",
                "content": "LINE:12 | SEVERITY:BLOCKER | SQL 注入：字符串拼接查询"}}],
            "usage": {"total_tokens": 100},
        })
    )

    model = OpenAIChatCompletionClient(model="gpt-4o-mini")
    security = [a for a in build_reviewers(model) if a.name == "security"][0]

    result = await security.run(task="SELECT * FROM users WHERE name = '" + "x" + "'")
    output = result.messages[-1].content

    # L1 断言：契约符合
    assert "LINE:" in output, "输出必须包含行号"
    assert "SEVERITY:" in output, "输出必须包含严重度"
    assert "BLOCKER" in output, "SQL 注入应判为 BLOCKER"

    await model.close()
```

**L1 检查清单**（每个 Agent 都要测）：

- [ ] 输出格式符合契约（Pydantic 校验或正则）；
- [ ] 属于自己职责的问题能识别（正例）；
- [ ] **不属于**自己职责的问题不越界（负例，如给安全专家一段"函数太长"的代码，应输出 `NO_ISSUE`）；
- [ ] 无问题时输出 `NO_ISSUE`（不编造）。

### 20.6.2 L2 协同级：测试交互与路由

**思路**：mock LLM，但**跑完整的多 Agent 流程**，断言"协作行为"而不只是"内容"。

这是**多 Agent 特有**的一层，也是本章重点。

```python
# tests/l2/test_collaboration.py
import pytest, respx
from httpx import Response
from review_team import build_reviewers, review_code

# ---- 多 Agent 特有的断言工具 ----
def assert_all_agents_spoke(result, expected_names: set[str]):
    """断言：每个 Agent 都发了言（没人被跳过）"""
    sources = {m.source for m in result.messages}
    assert expected_names <= sources, f"有 Agent 没发言: {expected_names - sources}"

def assert_speaking_order(result, order: list[str]):
    """断言：发言顺序符合预期（拓扑正确）"""
    actual = [m.source for m in result.messages if m.source in order]
    assert actual == order, f"发言顺序错误\n期望: {order}\n实际: {actual}"

def assert_no_role_violation(result, name: str, forbidden_keywords: list[str]):
    """断言：某 Agent 没有越界（说了不该说的话）"""
    msgs = [m.content for m in result.messages if m.source == name]
    for m in msgs:
        for kw in forbidden_keywords:
            assert kw not in m, f"[{name}] 角色越界，提到了: {kw}"

def assert_terminated(result, max_messages: int):
    """断言：流程正常终止，没有死循环"""
    assert len(result.messages) <= max_messages, f"消息数 {len(result.messages)} 超上限，疑似死循环"

def assert_context_not_exploding(result, max_total_chars: int = 50000):
    """断言：上下文没有爆炸增长"""
    total = sum(len(m.content or "") for m in result.messages)
    assert total <= max_total_chars, f"上下文总量 {total} 字符，疑似膨胀"
```

**实际测试**：

```python
@respx.mock
@pytest.mark.asyncio
async def test_collaboration_topology():
    # mock：让每个 Agent 都返回自己的固定话术
    respx.post("https://api.openai.com/v1/chat/completions").mock(
        side_effect=[
            Response(200, json={"choices": [{"message": {"role": "assistant", "content": "LINE:1 | SEVERITY:MAJOR | 硬编码密钥"}}], "usage": {"total_tokens": 50}}),   # security
            Response(200, json={"choices": [{"message": {"role": "assistant", "content": "LINE:5 | SEVERITY:MINOR | 循环内查询"}}], "usage": {"total_tokens": 50}}),   # performance
            Response(200, json={"choices": [{"message": {"role": "assistant", "content": "NO_ISSUE"}}], "usage": {"total_tokens": 30}}),                              # style
            Response(200, json={"choices": [{"message": {"role": "assistant", "content": "LINE:1 | SEVERITY:MAJOR | 缺少边界测试"}}], "usage": {"total_tokens": 40}}),  # test
            Response(200, json={"choices": [{"message": {"role": "assistant", "content": "VERDICT: REQUEST_CHANGES\n1. 硬编码密钥(BLOCKER)\n2. 循环内查询(MINOR)\nALL_DONE"}}], "usage": {"total_tokens": 80}}),  # aggregator
        ]
    )

    model = OpenAIChatCompletionClient(model="gpt-4o-mini")
    agents = build_reviewers(model)
    from autogen_agentchat.teams import RoundRobinGroupChat
    from autogen_agentchat.conditions import MaxMessageTermination

    team = RoundRobinGroupChat(
        participants=agents,
        termination_condition=MaxMessageTermination(len(agents)),
    )
    result = await team.run(task="评审这段代码：...")

    # ---- L2 断言 ----
    assert_all_agents_spoke(result, {"security", "performance", "style", "test", "aggregator"})
    assert_speaking_order(result, ["security", "performance", "style", "test", "aggregator"])
    assert_no_role_violation(result, "security", ["命名规范", "缺少测试"])   # 安全专家不该管命名/测试
    assert_terminated(result, max_messages=10)
    assert_context_not_exploding(result)

    # 聚合器结论断言
    agg = [m for m in result.messages if m.source == "aggregator"][-1]
    assert "VERDICT:" in agg.content
    assert "REQUEST_CHANGES" in agg.content

    await model.close()
```

**L2 检查清单**（多 Agent 特有）：

- [ ] **路由正确**：该谁发言时轮到了它（拓扑对）；
- [ ] **全员参与**：没有 Agent 被跳过（尤其聚合器不能缺席）；
- [ ] **角色不越界**：安全专家不评价命名，风格专家不提 SQL 注入；
- [ ] **正常终止**：消息数在硬上限内，不死循环；
- [ ] **交接完整**：聚合器收到的输入包含全部 4 位专家的意见；
- [ ] **上下文不爆炸**：总字符数在预算内。

### 20.6.3 L3 端到端级：真实质量 + 成本 + 延迟

**思路**：用真 LLM 跑 Golden Set，断言最终质量 + 成本 + 延迟。

```python
# tests/l3/test_e2e.py
import json, time, pytest
from review_team import review_code

GOLDEN = [json.loads(l) for l in open("tests/l3/golden.jsonl")]

PRICING = {"gpt-4o": (5.0/1e6, 15.0/1e6)}

@pytest.mark.e2e
@pytest.mark.parametrize("case", GOLDEN, ids=[c["id"] for c in GOLDEN])
@pytest.mark.asyncio
async def test_e2e_review_quality(case):
    code = open(case["code_file"]).read()

    start = time.perf_counter()
    verdict_text = await review_code(code)
    elapsed = time.perf_counter() - start

    # 1. 质量断言：必须发现指定的关键问题
    for issue in case.get("must_detect", []):
        assert issue.lower() in verdict_text.lower(), \
            f"[{case['id']}] 未检出问题: {issue}"

    # 2. 误报断言：不该报的不能报
    for fp in case.get("must_not_detect", []):
        assert fp.lower() not in verdict_text.lower(), \
            f"[{case['id']}] 误报: {fp}"

    # 3. 结论断言
    assert case["expected_verdict"] in verdict_text, \
        f"[{case['id']}] 结论不符，期望 {case['expected_verdict']}"

    # 4. 延迟门禁
    assert elapsed < case.get("max_seconds", 60), f"[{case['id']}] 超时 {elapsed:.1f}s"
```

**Golden Set 示例**（`tests/l3/golden.jsonl`）：

```jsonl
{"id":"rev-001","code_file":"samples/insecure_code.py","must_detect":["硬编码密钥","SQL 注入"],"must_not_detect":["命名"],"expected_verdict":"REQUEST_CHANGES","max_seconds":60}
{"id":"rev-002","code_file":"samples/clean_code.py","must_detect":[],"must_not_detect":["BLOCKER"],"expected_verdict":"APPROVE","max_seconds":60}
{"id":"rev-003","code_file":"samples/perf_code.py","must_detect":["N+1","循环"],"expected_verdict":"REQUEST_CHANGES","max_seconds":60}
```

### 20.6.4 成本门禁：多 Agent 的专属红线

多 Agent 成本是**乘法**的，必须单独设门禁：

```python
# conftest.py —— 成本追踪
import pytest

PRICING = {"gpt-4o": (5.0/1e6, 15.0/1e6)}

@pytest.fixture
def cost_tracker():
    """追踪本次测试所有 LLM 调用的累计成本"""
    calls = []

    def record(model, input_tokens, output_tokens):
        pin, pout = PRICING.get(model, (0, 0))
        cost = input_tokens * pin + output_tokens * pout
        calls.append({"model": model, "cost": cost})
        return cost

    yield record

    total = sum(c["cost"] for c in calls)
    print(f"\n💰 本次测试成本: ${total:.4f} ({len(calls)} 次调用)")
    # 硬门禁
    MAX_PER_TEST = 0.10   # 单个测试最多 $0.10
    assert total <= MAX_PER_TEST, f"成本 ${total:.4f} 超单测上限 ${MAX_PER_TEST}"
```

---

## 20.7 实战：完整的可运行工程

### 20.7.1 项目结构

```
multi_agent_review/
├── multi_agent_review/
│   ├── __init__.py
│   ├── agents.py           # 5 个 Agent 定义（角色/prompt）
│   ├── contracts.py        # Pydantic 消息契约
│   ├── team.py             # AutoGen 编排
│   └── mini_framework.py   # 20.4 手写框架（教学）
├── tests/
│   ├── conftest.py         # 共享 fixture（cost_tracker, mock_llm）
│   ├── l1/
│   │   └── test_single_agent.py       # 单 Agent 隔离测试
│   ├── l2/
│   │   ├── assertions.py              # 多 Agent 断言工具
│   │   └── test_collaboration.py       # 协同测试
│   ├── l3/
│   │   ├── golden.jsonl
│   │   ├── samples/
│   │   │   ├── insecure_code.py
│   │   │   ├── clean_code.py
│   │   │   └── perf_code.py
│   │   └── test_e2e.py
│   └── cassettes/          # vcrpy 录制回放
├── pytest.ini
├── pyproject.toml
└── .github/workflows/ci.yml
```

### 20.7.2 `pytest.ini` 分层

```ini
[pytest]
testpaths = tests
asyncio_mode = auto
markers =
    l1: 单 Agent 级（快，mock LLM，每次提交跑）
    l2: 协同级（中速，mock LLM，PR 必跑）
    l3: 端到端（慢，真 LLM，每天定时跑）
addopts = -ra --strict-markers --tb=short
```

### 20.7.3 GitHub Actions CI

```yaml
name: Multi-Agent CI
on: [push, pull_request, schedule]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with: {python-version: "3.11", cache: pip}
      - run: pip install -e ".[dev]"

      # L1 单 Agent 级（秒级，必过）
      - name: L1 Single Agent
        run: pytest -m l1 --cov=multi_agent_review --cov-fail-under=85

      # L2 协同级（分钟级，PR 必过）
      - name: L2 Collaboration
        run: pytest -m l2

      # L3 端到端（贵，仅 main + 每天凌晨 2 点）
      - name: L3 E2E
        if: github.ref == 'refs/heads/main' || github.event_name == 'schedule'
        env:
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
        run: pytest -m l3
```

### 20.7.4 运行效果示意

```bash
$ pytest -m "l1 or l2" -v

tests/l1/test_single_agent.py::test_security_agent_detects_sql_injection PASSED [ 20%]
tests/l1/test_single_agent.py::test_style_agent_no_false_positive        PASSED [ 40%]
tests/l2/test_collaboration.py::test_collaboration_topology              PASSED [ 60%]
tests/l2/test_collaboration.py::test_no_role_violation                   PASSED [ 80%]
tests/l2/test_collaboration.py::test_terminates_within_limit             PASSED [100%]

💰 本次测试成本: $0.0000 (0 次调用)   ← mock 模式，零成本
========================= 5 passed in 3.21s ==========================
```

> **注意 L1/L2 成本是 $0** —— 因为全部 mock。这正是"能隔离就隔离"的价值：**99% 的质量问题在零成本的前两层就能发现**。

---

## 20.8 多 Agent 调试技巧

### 20.8.1 定位"第一个出错的 Agent"

多 Agent 出错时，别看最后一个 Agent——要看**第一个偏离预期**的：

```python
def find_first_anomaly(result, checker):
    """找出第一个输出不符合预期的 Agent"""
    for i, msg in enumerate(result.messages):
        if msg.source == "user":
            continue
        if not checker(msg.source, msg.content):
            print(f"❌ 第一个异常在 [{msg.source}] (第 {i} 条)")
            print(f"   内容: {msg.content[:200]}")
            return msg
    return None
```

### 20.8.2 打印完整消息链路

```python
def dump_conversation(result):
    for i, m in enumerate(result.messages):
        print(f"\n─── [{i}] {m.source} ───")
        print(m.content)

# 失败时自动 dump
@pytest.fixture(autouse=True)
def dump_on_failure(request):
    yield
    if hasattr(request.node, "rep_call") and request.node.rep_call.failed:
        dump_conversation(get_last_result())
```

### 20.8.3 用"固定剧本"mock 复现 bug

线上出 bug 时，把那次的 LLM 响应录下来（vcrpy），作为回归用例：

```python
@pytest.mark.vcr("tests/cassettes/bug_rev_042.yaml")
def test_bug_regression():
    """复现 2026-10-15 线上事故：安全专家漏报了硬编码密钥"""
    ...
```

---

## 20.9 常见踩坑

| 坑 | 症状 | 解决 |
| --- | --- | --- |
| **角色越界** | 安全专家开始评价命名规范 | prompt 里显式写【禁止】+ L2 加 `assert_no_role_violation` |
| **死循环** | Writer ↔ Critic 无限互改 | `MaxMessageTermination` 硬上限 + `asyncio.wait_for` 超时 |
| **聚合器缺席** | 流程结束但没有最终结论 | L2 断言"全员参与"，聚合器必须是最后一个 |
| **交接丢参** | Handoff 时下游拿不到上游结果 | 用 Pydantic 定义交接契约，断言字段完整 |
| **上下文爆炸** | 10 轮后 token 超 100k | 每轮做摘要压缩（`summary_method`）+ L2 断言总量 |
| **成本失控** | 一次评审烧 $2 | 便宜模型做专家（mini）、贵模型只做聚合 + 成本门禁 |
| **mock 失真** | mock 的 LLM 表现与真实差太远 | 关键路径用 vcrpy 录真实响应，不用手写 mock |
| **测试互相污染** | 一个测试改了全局 client 影响后面 | 每个测试独立 `model_client`，用完 `close()` |
| **L3 不稳定** | 真 LLM 评测偶尔挂 | `xfail(strict=False)` 标记 flaky 用例 + 多次跑取平均 |
| **拓扑选错** | 用群聊做确定性流程，调试到崩溃 | 回归 20.2.5 决策树：能用流水线别用群聊 |

---

## 20.10 面试高频问题

**Q1. 多 Agent 系统和单 Agent 相比，测试上最大的区别是什么？**

A：单 Agent 测"输入→输出"；多 Agent 还要测**交互图**——路由是否正确（该谁发言）、交接是否完整、有没有死循环、有没有角色越界。断言类型从"内容正确"扩展到"协作行为正确"。

**Q2. 为什么多 Agent 的 Harness 要分三层？**

A：成本与定位效率的权衡。L1（单 Agent）秒级、零成本、能定位到具体 Agent；L2（协同）分钟级、零成本、能定位到交互边；L3（端到端）分钟级、真钱、只验证最终质量。**99% 的 bug 应该在 L1/L2 零成本发现**，L3 只做最终把关。

**Q3. 怎样防止多 Agent 死循环？**

A：五层保险——① `MaxMessageTermination` 硬轮数上限；② `asyncio.wait_for` 超时；③ 显式终止词（`ALL_DONE`）；④ 成本熔断（超预算直接停）；⑤ 关键决策交人工（`UserProxyAgent`）。前两条必须有。

**Q4. 多 Agent 的成本怎么控制？**

A：① 便宜模型做"打下手"的 Agent，贵模型只做聚合/关键决策；② 控制轮数；③ 每轮摘要压缩上下文；④ 并行能并行的（`asyncio.gather` 跑多个独立专家）；⑤ Harness 里加成本门禁，超阈值直接 fail。

**Q5. 手写多 Agent 和用框架（AutoGen/LangGraph）怎么选？**

A：学习/极简场景手写（100 行搞定，理解原理）；生产用框架（处理了并发、终止、观测、错误恢复）。但要理解框架本质——`SelectorGroupChat` 就是"让 LLM 选下一个发言人"这一件事。

**Q6. 四种拓扑怎么选？**

A：能并行的独立子任务 → 主从编排；固定几步 → 流水线；需要互相质疑迭代 → 圆桌群聊；按意图分发 → 对等移交。**能用简单拓扑别用复杂拓扑**，群聊调试成本最高。

**Q7. 多 Agent 的"角色越界"怎么测？**

A：给某个 Agent 一段"属于别人职责"的输入，断言它输出 `NO_ISSUE`（不越界）。例如给安全专家一段"函数太长"的代码，它应该不报风格问题。这是 L2 的核心断言之一。

---

## 20.11 本章小结

| 你学到了 | 对应技能点 |
| --- | --- |
| 多 Agent 失效模式 | 错误传播、角色越界、死循环、成本叠加 |
| Harness 三层门禁 | L1 单 Agent / L2 协同 / L3 端到端 |
| 四种协作拓扑 | 流水线 / 主从 / 圆桌 / 移交 + 选型决策树 |
| 编写五步法 | 拆解 → 角色 → 协议 → 编排 → 兜底 |
| system prompt 四要素 | 身份 / 边界 / 输入契约 / 输出契约 |
| 手写最小框架 | 消息总线 + 轮询调度 + 动态选人（100 行） |
| AutoGen 落地 | 四专家 + 聚合器代码评审系统 |
| 多 Agent 特有断言 | 路由 / 全员参与 / 不越界 / 不死循环 / 不爆炸 |
| 成本门禁 | token 计费 + 单测硬上限 |
| 调试技巧 | 定位第一个异常 Agent + 固定剧本复现 |

**核心心法**：

> 多 Agent 的复杂度是**乘法**的，所以 Harness 必须是**分层**的——
> **能隔离就隔离**（L1），**能便宜就便宜**（L2 用 mock），**只有最后一道才花钱**（L3）。

**全系列收束**：第 17 章学会编排多 Agent，第 18 章学会质量护栏，本章把两者合成"**敢上线的多 Agent 系统**"——至此 Python AI 工程化体系（LLM 基础 → Agent → RAG → 编排 → MCP → 评测 → 多 Agent → Harness）完整闭环。
