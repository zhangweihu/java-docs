# 第十章 AI Agent 开发：OpenAI SDK（从 Function Calling 到多 Agent 协作）

> 本章目标：从"调 LLM 接口做问答"升级为"**开发真正能干活、会调用工具、多角色协作的 AI Agent**"。先讲透 Agent 的核心原理（Agent Loop 与 Function Calling），再手写一个 80 行的 Agent 循环理解底层机制，最后切换到官方 **OpenAI Agents SDK**（openai-agents）做生产级开发——工具、多 Agent 交接、护栏、记忆、追踪一网打尽，并给出一个完整可运行的"智能客服工单助手"实战。
>
> 前置知识：第一章~第九章全部内容（尤其第 6 章 Pydantic、第 4 章 asyncio、第 9 章工程化）。这是 Python 体系的 **AI 扩展章**，与 Java 体系的 Spring AI 形成对照。
>
> 环境准备：`pip install openai openai-agents`，并设置环境变量 `OPENAI_API_KEY`。国内用户如使用代理/中转服务，改 `base_url` 即可（文中示例兼容）。

## 10.1 Agent 是什么：从"问答"到"行动"

### 10.1.1 LLM 应用的三次进化

| 阶段 | 形态 | 特点 | 代表作 |
| --- | --- | --- | --- |
| 1.0 Chatbot | 纯对话 | 只能"说"，不能"做" | 早期 ChatGPT 网页版 |
| 2.0 RAG | 对话 + 知识库 | 会"查资料"，但查完还是只能回答 | 企业知识库问答 |
| 3.0 Agent | 对话 + 知识库 + 工具 | 会**调用工具改变现实**（查订单、发邮件、写代码、操控系统） | OpenAI Deep Research、Manus、Cursor |

一句话总结三者的区别：

- **Chatbot**：你问它答，信息都在模型参数里；
- **RAG**：你问它，它先去数据库/文档里**检索**再答，信息来自外部知识；
- **Agent**：你给它目标，它自己**规划 → 调用工具 → 看结果 → 再规划**，直到完成目标。

### 10.1.2 Agent 的经典定义与核心循环

Agent = **LLM（大脑）+ 工具（手脚）+ 循环（执行机制）**。

```
┌──────────────────────────  Agent Loop（代理循环）  ──────────────────────────┐
│                                                                              │
│  用户输入 ──► LLM 推理 ──► 要调用工具？ ──是──► 执行工具 ──► 结果回填 ──┐     │
│                │              │                                                │
│                │              └──否──► 直接给出最终答案 ──► 返回用户            │
│                └──────────────────────────────────────────────────┘            │
│                              （循环直到：不再调用工具 或 达到最大轮数）          │
└──────────────────────────────────────────────────────────────────────────────┘
```

这个循环有专有名词——**ReAct**（Reasoning + Acting，推理与行动交替）。模型每一步先"想"（reasoning），再"做"（acting），把外部工具结果当新输入继续想。这是当前几乎所有 Agent 框架（OpenAI Agents SDK、LangGraph、AutoGen、Spring AI 的 Tool Calling）的底层范式。

### 10.1.3 为什么 Python 是 Agent 开发主场

Agent 本质是"**胶水代码**"：串起 LLM、工具、数据库、调度。Python 在三个层面占优：

| 层面 | Python | Java（对照） |
| --- | --- | --- |
| 官方 SDK | `openai` / `openai-agents`（迭代最快） | openai-java、Spring AI（相对滞后） |
| 工具生态 | 数据/AI 库全是 Python 亲儿子（Pandas、requests、FastAPI） | 生态在企业后端 |
| 快速迭代 | 写 Agent 原型小时级 | 框架较重，适合生产集成 |

> **Java 对照**：Java 里的对应物是 **Spring AI**（`ChatClient` + `@Tool` 注解），原理一模一样，只是 DSL 风格不同。如果你已经会用 Spring AI，本章的内容迁移成本极低——工具注解 `@function_tool` ≈ `@Tool`，Agent 循环 ≈ `ChatClient.prompt().tools(...)`。

## 10.2 openai SDK 基础：先学会"问"

Agent 的地基是"调用 LLM"。先掌握官方 `openai` SDK 的最小用法。

### 10.2.1 安装与配置

```bash
pip install openai
```

```bash
# Windows PowerShell（临时，推荐放用户环境变量）
$env:OPENAI_API_KEY = "sk-..."
# Linux/Mac
export OPENAI_API_KEY="sk-..."
```

```python
# 1_hello.py —— 30 秒跑通
from openai import OpenAI

client = OpenAI()  # 自动读取环境变量 OPENAI_API_KEY

resp = client.chat.completions.create(
    model="gpt-4o-mini",          # 按账号可用模型替换（gpt-4o / gpt-5-mini 等）
    messages=[
        {"role": "system", "content": "你是一个 Python 助教，回答简洁。"},
        {"role": "user", "content": "Python 的 GIL 是什么？一句话解释。"},
    ],
)
print(resp.choices[0].message.content)
```

**messages 是核心**：每次对话都是传一整个消息列表，模型无状态，上下文全靠你拼。

| role | 含义 | 类比 |
| --- | --- | --- |
| `system` | 系统设定（人设/规则/约束） | Java 里的"拦截器配置" |
| `user` | 用户输入 | 请求参数 |
| `assistant` | 模型回复（多轮时回传） | 上一步的响应 |
| `tool` | 工具执行结果（下一节） | 服务调用返回 |

### 10.2.2 多轮对话与流式输出

```python
# 2_chat.py —— 多轮对话 + 流式
from openai import OpenAI

client = OpenAI()
messages = [{"role": "system", "content": "你是一个极简主义者，回答不超过 20 字。"}]

while True:
    user = input("你：")
    if user.lower() in ("quit", "exit"):
        break
    messages.append({"role": "user", "content": user})

    stream = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=messages,
        stream=True,               # 流式：打字机效果，长回复体验好
    )
    print("AI：", end="", flush=True)
    reply = ""
    for chunk in stream:
        delta = chunk.choices[0].delta.content or ""
        print(delta, end="", flush=True)
        reply += delta
    print("\n")
    messages.append({"role": "assistant", "content": reply})  # 回传历史！
```

> 多轮对话的本质：**把整个历史 messages 每次全量传回去**。模型不记得你之前说过什么，是程序在"记"。这个思路在 Agent 里就是"记忆"的雏形。

### 10.2.3 结构化输出（衔接第 6 章 Pydantic）

```python
from openai import OpenAI
from pydantic import BaseModel

client = OpenAI()

class Movie(BaseModel):
    title: str
    year: int
    rating: float

resp = client.beta.chat.completions.parse(   # parse 模式自动用 JSON Schema 约束
    model="gpt-4o-mini",
    messages=[{"role": "user", "content": "推荐一部 2020 年后的科幻电影"}],
    response_format=Movie,                   # 传 Pydantic 模型！
)
movie = resp.choices[0].message.parsed      # 直接得到 Movie 对象
print(movie.title, movie.year, movie.rating)
```

> **对比 Java**：`response_format=Movie` ≈ Jackson 反序列化 + `@Valid` 校验一次做完。Pydantic 在 Agent 开发里是标配（工具的入参/出参全靠它）。

## 10.3 Function Calling：给 LLM 装上"手"

### 10.3.1 为什么需要工具调用

LLM 有三个天然缺陷，靠纯对话永远解决不了：

1. **没有实时性**：训练数据有截止日期，不知道今天的天气/股价/快递状态；
2. **没有私有数据**：看不到你数据库里的订单、内网文档；
3. **算不准**：精确的数学运算、字符串处理会一本正经地胡说。

Function Calling（函数调用）就是解法：**让模型"请求"调用你提供的函数，由你的程序真正执行**。

### 10.3.2 完整机制（必须理解的三步）

```
步骤1：你把"有哪些工具、每个工具长什么样"告诉模型（tools 参数 + JSON Schema 描述）
步骤2：模型"决定"要不要调用、调用哪个、传什么参数（返回 tool_calls，但不执行！）
步骤3：你的程序执行工具，把结果以 tool 角色消息回填，模型基于结果继续回答
```

关键点：**模型从不真正执行代码，它只负责"决定"，执行权永远在你手里**。这一步是安全边界所在。

### 10.3.3 手写完整流程（openai SDK 原生）

```python
# 3_function_calling.py —— 原生 Function Calling 全流程
from openai import OpenAI

client = OpenAI()

# ── 步骤 0：定义真实工具（Python 函数）──
def get_weather(city: str) -> str:
    """真实的天气查询（这里用模拟数据）"""
    table = {"北京": "晴，28°C", "上海": "小雨，25°C", "广州": "多云，31°C"}
    return table.get(city, f"{city} 天气数据暂缺")

def calc(expr: str) -> str:
    """安全的计算器：只允许数字和四则运算"""
    allowed = set("0123456789+-*/(). ")
    if not set(expr) <= allowed:
        return "只支持四则运算"
    return str(eval(expr))  # 演示用；生产用 ast 解析或调用计算服务

# ── 步骤 1：把工具描述给模型（JSON Schema 自动从函数签名手写映射）──
tools = [
    {
        "type": "function",
        "function": {
            "name": "get_weather",
            "description": "查询指定城市的当前天气",
            "parameters": {
                "type": "object",
                "properties": {"city": {"type": "string", "description": "城市名"}},
                "required": ["city"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "calc",
            "description": "执行四则运算表达式",
            "parameters": {
                "type": "object",
                "properties": {"expr": {"type": "string"}},
                "required": ["expr"],
            },
        },
    },
]

FUNCTIONS = {"get_weather": get_weather, "calc": calc}

# ── 步骤 2+3：Agent Loop（简化版）──
def run_agent(user_input: str, max_turns: int = 3) -> str:
    messages = [{"role": "user", "content": user_input}]
    for _ in range(max_turns):
        resp = client.chat.completions.create(
            model="gpt-4o-mini", messages=messages, tools=tools
        )
        msg = resp.choices[0].message
        messages.append(msg)                     # 回传 assistant 消息（可能带 tool_calls）

        if not msg.tool_calls:                   # 模型不再要工具 → 输出最终答案
            return msg.content or "（无输出）"

        for tc in msg.tool_calls:                # 模型可能一次要调多个工具
            name, args = tc.function.name, tc.function.arguments
            print(f"  → 调用工具 {name}({args})")
            result = FUNCTIONS[name](**eval(args))  # 执行真实工具！
            messages.append({                    # 结果以 tool 角色回填
                "role": "tool",
                "tool_call_id": tc.id,           # 必须对应！模型靠它关联
                "content": str(result),
            })
    return "达到最大轮数，已停止。"

if __name__ == "__main__":
    print(run_agent("北京和上海天气各是多少？顺便算一下 12*13+7"))
```

运行输出大致为：

```
  → 调用工具 get_weather({"city":"北京"})
  → 调用工具 get_weather({"city":"上海"})
  → 调用工具 calc({"expr":"12*13+7"})
北京今天晴，28°C；上海小雨，25°C。12*13+7 = 163。
```

**四个必踩的坑**（面试常问）：

| 坑 | 现象 | 解法 |
| --- | --- | --- |
| tool 结果没转字符串 | 模型收到非文本 | `content=str(result)` 强制转 |
| `tool_call_id` 对不上 | 模型报"关联错误" | 回填时原样复制 `tc.id` |
| 死循环 | 工具反复被调 | `max_turns` 硬上限（防 token 烧钱） |
| 空 tool_calls / 空 content | 模型犹豫 | 兜底返回"（无输出）"并结束 |

## 10.4 手写一个 Agent Loop：彻底搞懂原理

上一节的 `run_agent` 就是最简 Agent Loop。现在我们把它升级成**带记忆、带多轮对话、带 ReAct 风格**的完整版本——这一步做完，你对任何 Agent 框架的理解都会"透视"。

```python
# 4_agent_loop.py —— 自研 Agent Loop：记忆 + 工具 + 多轮对话
from openai import OpenAI
import json

client = OpenAI()

# ── 工具注册表：装饰器让"加工具"变成一行 ──
TOOLS = {}

def tool(name: str, description: str, parameters: dict):
    def decorator(fn):
        TOOLS[name] = fn
        TOOLS[name + "__schema__"] = {
            "type": "function",
            "function": {"name": name, "description": description, "parameters": parameters},
        }
        return fn
    return decorator

@tool("get_weather", "查询城市天气", {"type": "object", "properties": {"city": {"type": "string"}}, "required": ["city"]})
def get_weather(city: str) -> str:
    return {"北京": "晴 28°C", "上海": "小雨 25°C"}.get(city, f"{city} 暂无数据")

@tool("get_time", "获取当前时间", {"type": "object", "properties": {}, "required": []})
def get_time() -> str:
    from datetime import datetime
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")

class Agent:
    """迷你 Agent：持有一段记忆（消息历史），循环调用模型与工具"""
    def __init__(self, system: str, max_turns: int = 5):
        self.messages = [{"role": "system", "content": system}]
        self.max_turns = max_turns
        self.tools = [v for k, v in TOOLS.items() if k.endswith("__schema__")]

    def chat(self, user_input: str) -> str:
        self.messages.append({"role": "user", "content": user_input})
        for _ in range(self.max_turns):
            resp = client.chat.completions.create(
                model="gpt-4o-mini", messages=self.messages, tools=self.tools
            )
            msg = resp.choices[0].message
            self.messages.append(msg)
            if not msg.tool_calls:
                return msg.content or "（无输出）"
            for tc in msg.tool_calls:
                name = tc.function.name
                args = json.loads(tc.function.arguments or "{}")
                print(f"  [Agent] 调用 {name}{args}")
                result = TOOLS[name](**args)
                self.messages.append({
                    "role": "tool", "tool_call_id": tc.id, "content": str(result),
                })
        return "达到最大轮数。"

if __name__ == "__main__":
    agent = Agent(system="你是生活助手，天气和时间问题用工具回答。")
    print(agent.chat("现在几点？"))
    print(agent.chat("北京天气呢？"))   # 记忆在 self.messages 里，第二句仍记得上文
```

**这个自研 Agent 已经具备框架的三大要素**：

1. **记忆**：`self.messages` 贯穿全程，跨轮保留（对比 10.2.2 的多轮对话）；
2. **工具注册**：装饰器注册表，加工具只写一个函数（对比 10.5 的 `@function_tool`）；
3. **循环控制**：`max_turns` 终止条件，防止无限调用。

> **为什么还要用框架？** 自研版在大约 80 行内理解了原理，但生产环境还缺：参数 Pydantic 校验、工具并发执行、重试/超时、会话持久化、追踪观测、多 Agent 编排……这些正是下一节官方 SDK 替你解决的事。**先懂原理，再用框架，是 Agent 学习的最优路径。**

## 10.5 OpenAI Agents SDK 快速上手

OpenAI 官方 Agent 框架（`openai-agents`，由早期实验项目 Swarm 演进而来），Python 优先，设计极简。

### 10.5.1 安装与 Hello World

```bash
pip install openai-agents
```

```python
# 5_hello_agents.py
from agents import Agent, Runner

agent = Agent(name="Assistant", instructions="You are a helpful assistant.")

result = Runner.run_sync(agent, "用一句话解释什么是 Agent。")
print(result.final_output)
```

**两大核心概念**：

| 概念 | 作用 | 类比 |
| --- | --- | --- |
| `Agent` | 定义"智能体"：名字 + 指令（人设）+ 工具 + 交接对象 | Spring AI 里的 `ChatClient` + 配置 |
| `Runner` | 执行 Agent Loop 的"引擎" | Controller 里的调用入口 |

Runner 三种运行方式（对应第 4 章 asyncio）：

```python
result = Runner.run_sync(agent, "同步提问")          # 同步（脚本/CLI 用）
result = await Runner.run(agent, "异步提问")          # 异步（FastAPI 里用，await）
result = Runner.run_streamed(agent, "流式提问")       # 流式（打字机效果）
# result.final_output / result.to_input_list() 等拿到结果
```

### 10.5.2 Agent 的五要素配置

```python
from agents import Agent

agent = Agent(
    name="客服助手",
    instructions="你是电商客服，礼貌简洁。用工具查订单，查不到就转人工。",
    model="gpt-4o-mini",          # 默认模型（也可在 Runner 里覆盖）
    tools=[...],                   # 工具列表（10.6）
    handoffs=[...],                # 可交接的其它 Agent（10.7）
    input_guardrails=[...],        # 输入护栏
    output_guardrails=[...],       # 输出护栏
)
```

> **对照 Java**：`instructions` ≈ system prompt（写在配置里而非硬编码字符串）；`Agent` 配置即对象，和 Spring 的 Bean 配置异曲同工。

## 10.6 工具开发：@function_tool 与类型安全

### 10.6.1 装饰器一行注册（对比 10.4 自研版）

```python
# 6_tools.py
from agents import Agent, Runner, function_tool
from pydantic import BaseModel, Field

# 任意 Python 函数 + 装饰器 = 工具。类型注解自动生成 JSON Schema！
@function_tool
def get_weather(city: str) -> str:
    """查询指定城市当前天气"""     # docstring 自动成为工具描述
    return f"{city}：晴，28°C"

# 复杂参数：用 Pydantic 模型做入参（自动校验，参数错了模型会收到校验错误）
class OrderQuery(BaseModel):
    order_id: str = Field(..., description="订单号，形如 OD2026xxxx")

@function_tool
def query_order(q: OrderQuery) -> dict:
    """根据订单号查询订单状态"""
    return {"order_id": q.order_id, "status": "已发货", "eta": "2026-08-21"}

agent = Agent(
    name="订单助手",
    instructions="查询订单时调用工具，注意订单号格式。",
    tools=[get_weather, query_order],
)

result = Runner.run_sync(agent, "帮我查一下 OD2026081901 的状态")
print(result.final_output)
```

**SDK 自动完成的四件事**（对比 10.3 手写时都要自己做）：

| 手写版要自己做的 | SDK 自动做 |
| --- | --- |
| 手写 JSON Schema | 从类型注解 + docstring 自动生成 |
| `json.loads` 解析参数 | Pydantic 自动解析 + 校验 |
| 手动把结果转字符串 | 自动序列化回填 |
| 手动遍历 tool_calls | Runner 内部完成整个循环 |

### 10.6.2 工具进阶：上下文与错误处理

```python
from agents import Agent, Runner, function_tool, RunContextWrapper

# 1) 需要"会话内状态"的工具：第一参数声明上下文
@function_tool
def add_note(ctx: RunContextWrapper, content: str) -> str:
    ctx.context["notes"].append(content)     # context 由 run 时传入
    return f"已记录：{content}"

# 2) 工具内部可安全抛异常，SDK 会把错误信息回传模型继续尝试
@function_tool
def divide(a: float, b: float) -> float:
    if b == 0:
        raise ValueError("除数不能为 0")      # 异常文本会作为工具结果回给模型
    return a / b

async def main():
    ctx = {"notes": []}
    agent = Agent(name="A", instructions="用工具记录和计算", tools=[add_note, divide])
    result = await Runner.run(agent, "记下'买牛奶'，然后算 10/0", context=ctx)
    print(result.final_output)
    print("会话笔记：", ctx["notes"])
```

> **安全提示**：工具是 Agent 的"能力边界"。只暴露最小权限——比如查询工具只读不写、写操作要求二次确认。模型并不真正"懂"你的系统，权限模型是被提示词左右的，永远按"不可信输入"对待。

### 10.6.3 接入 MCP 与已有服务

工具不限于本地函数——通过 MCP（Model Context Protocol）可接入任意外部服务（数据库、GitHub、浏览器），相当于"工具界的 JDBC"：

```python
from agents.mcp import MCPServerStdio, MCPServerSse

# 本地 stdio 服务
server = MCPServerStdio(command="npx", args=["-y", "some-mcp-server"])
# 远程 SSE 服务
server = MCPServerSse(endpoint="https://example.com/mcp")

agent = Agent(name="A", mcp_servers=[server])
```

## 10.7 多 Agent 协作：Handoffs、Guardrails、记忆与追踪

### 10.7.1 Handoffs：让专业 Agent 干专业事（主管-专员模式）

单 Agent 塞太多职责会"精神分裂"（指令冲突、工具混乱）。生产上常见**主管（triage）+ 专员（specialist）**结构：

```python
# 7_handoffs.py —— 客服主管 + 退款/账单专员
from agents import Agent, Runner

refund_agent = Agent(
    name="退款专员",
    instructions="处理退款：验证订单已签收、金额不超过 200 元，符合条件则回复'退款已受理'。",
)
billing_agent = Agent(
    name="账单专员",
    instructions="处理账单/支付问题：解释账单明细，指导开发票。",
)
triage_agent = Agent(
    name="客服主管",
    instructions="判断用户意图：退款问题交接给退款专员，账单问题交接给账单专员，其余自己回答。",
    handoffs=[refund_agent, billing_agent],   # 交接名单 = 能力清单
)

result = Runner.run_sync(triage_agent, "我昨天买的东西想退款")
print(result.final_output)
# 内部流程：主管判断 → 交接给退款专员 → 专员处理 → 结果返回
```

**Handoffs 的设计哲学**：

- 每个 Agent **单一职责**（一个 Agent 只做一件事），复杂度可控、可单独测试；
- 交接是"**带上下文的移交**"：接收方拿到完整对话历史，无需重新解释需求；
- 像"**部门协作**"：主管接单、分单、专员处理，各司其职。

> **对照 Java**：多 Agent ≈ 微服务按领域拆分；handoffs ≈ 服务间调用（但 Agent 间是 LLM 自动决策路由，而非硬编码 Feign 调用）。

### 10.7.2 Guardrails：输入输出护栏（安全阀）

LLM 输出不可控，护栏在**进入循环前（输入）**和**退出循环前（输出）**做校验拦截，触发即中断：

```python
from agents import Agent, Runner, GuardrailFunctionOutput, input_guardrail, output_guardrail
from pydantic import BaseModel

class Reply(BaseModel):
    content: str

# 输入护栏：拦截涉敏词，直接拒绝，不让模型继续
@input_guardrail
async def block_sensitive(agent, input_text: str) -> GuardrailFunctionOutput:
    if "密码" in input_text or "银行卡" in input_text:
        return GuardrailFunctionOutput(
            tripwire_triggered=True,
            tripwire_message="涉及敏感信息，请勿在对话中提供。",
        )
    return GuardrailFunctionOutput(tripwire_triggered=False)

# 输出护栏：拦截模型输出中的违规内容
@output_guardrail
async def no_pii(agent, output: Reply) -> GuardrailFunctionOutput:
    if "身份证" in output.content or "手机号" in output.content:
        return GuardrailFunctionOutput(
            tripwire_triggered=True,
            tripwire_message="输出包含个人敏感信息，已拦截。",
        )
    return GuardrailFunctionOutput(tripwire_triggered=False)

agent = Agent(
    name="安全助手",
    instructions="回答用户问题。",
    input_guardrails=[block_sensitive],     # 输入
    output_guardrails=[no_pii],             # 输出（需配合结构化输出）
)
```

> **护栏 vs 工具异常**：工具异常是"执行层容错"；护栏是"策略层拦截"——相当于 Java 里的参数校验注解（`@Valid`）+ 敏感词过滤器（Filter）。

### 10.7.3 Sessions：跨轮记忆自动管理

10.2 里"手动回传 messages"的痛苦，SDK 用 `Session` 解决——自动维护对话历史：

```python
from agents import Agent, Runner, Session

agent = Agent(name="Assistant", instructions="你是贴心助手。")
session = Session()                          # 一个会话 = 一段上下文

r1 = Runner.run_sync(agent, "我叫小明", session=session)
r2 = Runner.run_sync(agent, "我叫什么？", session=session)   # 自动带上历史
print(r2.final_output)   # 正确回答"小明"
```

生产中可以替换为 SQLite/Redis 等持久化 Session（服务重启不丢记忆），这也与第 6 章 SQLAlchemy、第 4 章 asyncio 衔接。

### 10.7.4 Tracing：看得见的 Agent 运行过程

Agent Loop 是多轮黑盒，出问题难排查。SDK 内置 Tracing（可观测性）：

```python
from agents.tracing import add_trace_processor, ConsoleSpanExporter

# 控制台打印每次 LLM 调用、工具调用、交接的完整轨迹
add_trace_processor(ConsoleSpanExporter())

from agents import Agent, Runner
agent = Agent(name="T", instructions="你是一个助手", tools=[my_tool])
result = Runner.run_sync(agent, "帮我查天气")
# 之后控制台/平台能看到：run → agent → llm call → tool call → llm call → final
```

> **对照 Java**：Tracing ≈ Micrometer/OpenTelemetry 的链路追踪（trace/span），只是 Agent 的"链路"是思考与工具调用。生产环境配合日志（第 3 章 logging）一起看。

## 10.8 实战：智能客服工单助手（完整可运行）

把 10.5~10.7 串成一个真实的客服系统：**主管分流 + 工具查单 + 退款专员 + 护栏 + 会话记忆**。

```python
# 8_support_agent.py —— 智能客服工单助手
import os
from agents import Agent, Runner, function_tool, Session
from pydantic import BaseModel, Field

# ── 1. 数据层：模拟订单库（生产可换成 6.5 的 SQLAlchemy）──
ORDERS = {
    "OD2026081901": {"status": "已发货", "eta": "2026-08-21", "amount": 129.0},
    "OD2026081902": {"status": "待付款", "eta": None, "amount": 399.0},
    "OD2026081903": {"status": "已签收", "eta": "2026-08-17", "amount": 55.0},
}

class OrderId(BaseModel):
    order_id: str = Field(..., description="订单号，形如 OD2026xxxx")

# ── 2. 工具：查询订单（只读，最小权限）──
@function_tool
def query_order(q: OrderId) -> str:
    """按订单号查询订单状态、预计送达、金额"""
    o = ORDERS.get(q.order_id)
    return str(o) if o else f"未找到订单 {q.order_id}，请让用户核对订单号"

# ── 3. 三个 Agent：主管 + 退款专员 + 订单查询员 ──
query_agent = Agent(
    name="订单查询员",
    instructions="收到订单号就用 query_order 工具查询并如实回复；查不到就请用户核对订单号。",
    tools=[query_order],
)

refund_agent = Agent(
    name="退款专员",
    instructions=(
        "处理退款申请：要求用户提供订单号并用 query_order 查状态。"
        "仅当订单已签收且金额≤200元时受理退款，否则礼貌说明原因。"
    ),
    tools=[query_order],
)

triage_agent = Agent(
    name="客服主管",
    instructions=(
        "你是客服主管，判断用户意图：\n"
        "1) 查订单/物流 → 交接给订单查询员；\n"
        "2) 退款/退货 → 交接给退款专员；\n"
        "3) 其他咨询 → 直接礼貌回答。"
    ),
    handoffs=[query_agent, refund_agent],
)

def main():
    print("智能客服工单助手已就绪（输入 quit 退出）\n")
    session = Session()          # 会话记忆：同一用户多轮上下文
    while True:
        user = input("用户：")
        if user.lower() == "quit":
            break
        result = Runner.run_sync(triage_agent, user, session=session)
        print(f"客服：{result.final_output}\n")

if __name__ == "__main__":
    main()
```

**体验流程**（三句话覆盖三类能力）：

```
用户：我的订单 OD2026081901 到哪了？
客服：（主管→订单查询员→query_order 工具）您的订单已发货，预计 08-21 送达。

用户：这个订单我想退款
客服：（主管→退款专员→query_order）订单金额 129 元且已发货，可受理退款……

用户：对了，还记得我第一个订单号吗？
客服：（Session 记忆）记得，是 OD2026081901。（若换成自研版需自己维护 messages）
```

**架构小结**——这个不到 60 行的小系统已经具备生产 Agent 的骨架：

| 能力 | 实现 |
| --- | --- |
| 意图路由 | triage 主管 `handoffs` |
| 外部数据 | `query_order` 工具（只读，可换数据库） |
| 业务规则 | refund 专员 `instructions` 硬约束（金额/状态门槛） |
| 记忆 | `Session` 跨轮保留 |
| 可扩展 | 加护栏（10.7.2）、加 Tracing（10.7.4）、加 MCP 接真实系统 |

## 10.9 安全、成本与工程化（Agent 上生产的必修课）

### 10.9.1 Prompt 注入与权限最小化

Agent 最危险的是 **Prompt 注入**：外部数据（网页内容、用户输入、工具结果）里藏指令，诱导模型执行非预期操作。

| 风险 | 场景 | 缓解 |
| --- | --- | --- |
| 工具越权 | 网页文本让 Agent 调用"删数据"工具 | 工具最小权限（只读优先）、写操作需人工确认 |
| 数据外泄 | 工具结果含敏感信息被拼进回复 | 输出护栏过滤 PII（10.7.2） |
| 指令劫持 | 用户输入试图覆盖 system 指令 | 把 system 指令放在最后、关键词策略（指令"主角意识"）、内容过滤 |

> **核心原则**：Agent 的权限边界 = 工具边界。**所有工具都按"不可信输入"设计**：参数校验（Pydantic）、返回值脱敏、写操作降权。这与你写 REST 接口时信任边界的设计思路完全一致。

### 10.9.2 成本与延迟控制

| 手段 | 说明 |
| --- | --- |
| 模型分层 | 简单任务 `gpt-4o-mini`，复杂任务才用大模型（路由成本差 10 倍+） |
| 缓存 | 相同/相似问题命中缓存（Semantic Cache），跳过 LLM 调用 |
| 轮数上限 | `max_turns` 防死循环烧 token（10.3.4 的坑在这里的工程化版本） |
| 上下文瘦身 | Session 太长时截断/摘要历史，控制 token 用量 |
| 流式输出 | 首字延迟更低，体验好 |

### 10.9.3 工程化闭环（衔接第 9 章）

Agent 也是代码，必须走 9 章的完整流水线：

- **测试**：把工具函数写成纯函数，pytest 单测（mock LLM 返回固定 tool_calls，用 `monkeypatch` 替换 Runner/客户端）；护栏逻辑重点覆盖；
- **类型**：mypy + Pydantic 双保险（模型入参出参全是类型安全）；
- **CI**：`.env` 里注入测试 key，GitHub Actions 里跑 `pytest` + `ruff`；
- **观测**：Tracing + logging 接入统一日志平台；
- **配置**：模型名、key、轮数上限全部走环境变量/`.env`（第 3 章配置管理），不进代码库。

## 10.10 练习与面试

### 本章练习

1. 用 `openai` SDK 写一个"翻译助手"：流式输出 + 多轮对话记忆（输入 quit 退出）；
2. 用 Function Calling 实现"股票查询"：工具返回模拟行情，模型负责汇总成一句话；
3. 修改 10.3.3 的 `run_agent`：把工具参数校验加进去（非法参数时模型应自动修正）；
4. 用 `openai-agents` 重写练习 2，对比手写版与框架版的代码量差异；
5. 给 10.8 的客服助手加一个**输入护栏**：拦截"帮我删库/转账"类危险指令；
6. 扩展客服助手：新增"发票专员"Agent，通过 `handoffs` 接入主管，支持"开发票"场景；
7. 给客服助手接入**持久化 Session**（用 SQLite，参考第 6 章），验证重启后记忆仍在；
8. 选做：把 10.8 的客服助手封装成 FastAPI 接口（第 6 章），用 `await Runner.run()` 提供服务。

### 面试题

| 问题 | 要点 |
| --- | --- |
| 什么是 Agent？和普通 LLM API 调用有什么区别？ | 能调用工具、有循环（ReAct）、目标是"做事"而非"回答" |
| 什么是 Agent Loop？ | LLM 推理 → 要工具则执行并回填 → 再推理，直到不调工具或达轮数上限 |
| Function Calling 的原理？ | 模型只返回调用"请求"（名字+参数），执行权在程序；结果以 tool 角色回填 |
| ReAct 是什么？ | Reasoning + Acting 交替的提示词范式 |
| Agent 的"记忆"怎么实现？ | 消息列表回传；框架用 Session 自动管理，可持久化 |
| 为什么要多 Agent（handoffs）？ | 单一职责、复杂度可控、可独立测试；主管路由 + 专员执行 |
| Guardrails 和工具异常处理区别？ | 护栏是策略层（输入输出拦截中断）；异常是执行层容错 |
| 工具调用死循环怎么防？ | max_turns 上限；工具幂等；观测轮数 |
| Prompt 注入怎么防？ | 工具最小权限、输入过滤、护栏、指令与外部数据隔离 |
| Agent 的成本怎么控制？ | 模型分层、缓存、轮数上限、历史瘦身 |
| 工具参数怎么保证类型安全？ | Pydantic 模型 + 类型注解（SDK 自动生成 schema 并校验） |
| 怎么排查 Agent 不按预期工作？ | Tracing 看每一步 LLM/工具调用；日志；逐步打印 messages |
| openai SDK 和 openai-agents 是什么关系？ | SDK 是底层 API 调用；agents 是官方 Agent 框架（基于 SDK 封装循环/工具/记忆） |
| 自研 Agent 和框架怎么选？ | 学习/定制用自研；生产推荐框架（成熟循环、观测、多 Agent、MCP） |

### 本章小结

- **Agent 本质**（10.1）：LLM + 工具 + 循环；ReAct 范式；从问答到行动；
- **openai SDK**（10.2）：messages 结构、多轮回传、流式、结构化输出（Pydantic）；
- **Function Calling**（10.3）：模型"决定"、程序"执行"、结果回填；四个必踩的坑；
- **手写 Agent Loop**（10.4）：记忆 + 工具注册 + 轮数上限，80 行理解全部原理；
- **Agents SDK**（10.5~10.7）：Agent/Runner、`@function_tool`、handoffs、guardrails、Session、Tracing；
- **实战**（10.8）：智能客服工单助手——主管分流 + 查单工具 + 退款专员 + 记忆；
- **上生产**（10.9）：Prompt 注入防御、成本控制、测试/CI/观测工程化闭环。

---

## 🚀 后续进阶方向（AI 方向路线图）

完成本章后，按兴趣继续深入：

- **框架进阶**：[12-LangGraph编排.md](./12-LangGraph编排.md)（状态机式编排，比 handoffs 更可控）、AutoGen、CrewAI；
- **知识增强**：[11-RAG与向量数据库.md](./11-RAG与向量数据库.md)——向量数据库（Chroma/pgvector/Qdrant）+ Embedding 检索，让 Agent 会"查资料"；
- **协议与生态**：[13-MCP工具接入.md](./13-MCP工具接入.md)（工具互通标准，一次开发处处可用）、OpenAI Responses API（新一代接口）；
- **多模态**：语音 Agent（speech-to-text → LLM → text-to-speech）、图像理解工具；
- **评测**：Agent 质量评测（基于 GPT 打分、模拟用户回归）、Prompt 工程系统化。

> **给 Java 开发者的最后一句话**：Agent 开发拼的不是语法，而是"**给模型搭好边界和工具**"的系统设计能力——权限边界、成本边界、质量边界。这个能力和你在 Java 后端设计接口、划服务边界是同一件事。工具会更新换代，这套方法论不会。

配套：[python-learning/README.md](./README.md) ｜ Java 侧可对照 [java-learning](../java-learning/README.md) 的 Spring AI 相关章节。
