# 第十二章 LangGraph 编排：把 Agent 流程画成"状态图"

> 本章目标：第 10 章的 Agent 循环是"一条直线"，第 11 章的 RAG 是一个节点——现实业务需要**分支、循环、回退、多人协作**的复杂流程。本章掌握 **LangGraph**（LangChain 团队的图编排框架）：用"状态机 + 有向图"声明式定义 Agent 工作流，支持条件路由、工具调用循环、记忆持久化与多 Agent 协作，最后完成一个可运行的**客服 + 工具 + 人工兜底**工作流实战。
>
> 前置知识：第 10 章（Agent 循环原理）、第 11 章（RAG）。对标 Java：Spring StateMachine（状态机）、BPMN/流程引擎（Activiti）。
>
> 环境准备：`pip install langgraph openai`，设置 `OPENAI_API_KEY`。

## 12.1 为什么需要编排框架

### 12.1.1 手写循环的局限（回顾第 10 章）

第 10.4 的 `while` 循环 Agent 能跑，但一复杂就失控：

| 痛点 | 手写循环的困境 |
| --- | --- |
| **流程不可见** | 分支/回退逻辑埋在 if-else 里，改不动、说不清 |
| **状态管理裸奔** | 消息列表全靠手拼，无版本、无检查点 |
| **不可恢复** | 某步失败无法从断点重跑，只能全量重来 |
| **无法编排** | 多 Agent 协作、并行、人工审批都靠手工胶水 |

### 12.1.2 LangGraph 的答案：图 = 流程，节点 = 步骤，边 = 流转

LangGraph 把工作流建模为**有向图**（StateGraph）：

- **State（状态）**：整个流程共享的数据结构（消息、中间结果）；
- **Node（节点）**：一个执行步骤（Python 函数，读状态、返回更新）；
- **Edge（边）**：节点间流转；**条件边**决定下一步去哪个节点；
- **START / END**：流程入口与出口。

```
 用户问题 ──► (入口)START
                  │
                  ▼
            [判断意图] ──需要工具──► [调用工具] ──► [LLM 汇总] ──► 完成
                  │                                                  ▲
                  └───────── 直接回答 ────────────────────────────────┘
```

> **对照 Java**：StateGraph ≈ Spring StateMachine 或 Activiti BPMN 流程定义；节点 ≈ Service 方法；条件边 ≈ 流程引擎里的条件跳转。只是 LangGraph 的"状态"是共享的 Python dict/TypedDict。

## 12.2 核心概念与最小图

### 12.2.1 State 用 TypedDict 定义

```python
from typing import TypedDict, Annotated, operator

class WorkflowState(TypedDict):
    question: str            # 输入问题
    messages: Annotated[list, operator.add]   # 消息累积（用 + 合并，便于多节点追加）
    result: str              # 最终答案
```

> `Annotated[list, operator.add]` 是 LangGraph 的"归约器"：每个节点返回的 messages 会被**追加**而不是覆盖。这是多节点协作的关键机制。

### 12.2.2 节点：普通函数

```python
def node_a(state: WorkflowState) -> dict:
    # 读 state，返回"要更新的字段"
    return {"result": f"处理了：{state['question']}"}
```

节点函数签名固定：**入参 state，出参 dict（要更新的字段）**。

### 12.2.3 最小线性图：编译 + 调用

```python
# 1_linear.py —— 最小线性工作流
from typing import TypedDict
from langgraph.graph import StateGraph, START, END

class State(TypedDict):
    content: str

def step_upper(state: State) -> dict:
    return {"content": state["content"].upper()}

def step_bang(state: State) -> dict:
    return {"content": state["content"] + "!!"}

# 1. 建图
builder = StateGraph(State)
builder.add_node("upper", step_upper)
builder.add_node("bang", step_bang)

# 2. 连边
builder.add_edge(START, "upper")
builder.add_edge("upper", "bang")
builder.add_edge("bang", END)

# 3. 编译 + 运行（编译后图不可再改）
graph = builder.compile()
print(graph.invoke({"content": "hello"}))   # {'content': 'HELLO!!'}
```

**三步套路**：`add_node` 定义步骤 → `add_edge`/`add_conditional_edges` 定义流转 → `compile().invoke()` 运行。所有复杂工作流都是这三步的组合。

## 12.3 条件路由与循环：流程的分支能力

### 12.3.1 条件边：根据状态决定下一步

```python
# 2_conditional.py —— 条件分支：AI 生成笑话，批评家打分，不过关就重写（循环！）
from typing import TypedDict
from langgraph.graph import StateGraph, START, END
from openai import OpenAI

client = OpenAI()

class State(TypedDict):
    topic: str
    joke: str
    score: int

def generate_joke(state: State) -> dict:
    r = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[{"role": "user",
                   "content": f"讲一个关于 {state['topic']} 的程序员冷笑话，一句话。"}],
    )
    return {"joke": r.choices[0].message.content}

def judge(state: State) -> dict:
    r = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[{"role": "user",
                   "content": f"给这个笑话打分(0-10)，只回数字：{state['joke']}"}],
    )
    return {"score": int(r.choices[0].message.content.strip()[:2])}

def route(state: State) -> str:
    # 条件边的路由函数：返回"下一个节点名"或 "END"
    return "improve" if state["score"] < 7 else "end"

def improve(state: State) -> dict:
    return {"joke": state["joke"] + "（加强版：再抖个包袱）"}

builder = StateGraph(State)
builder.add_node("gen", generate_joke)
builder.add_node("judge", judge)
builder.add_node("improve", improve)

builder.add_edge(START, "gen")
builder.add_edge("gen", "judge")
builder.add_conditional_edges("judge", route, {"improve": "improve", "end": END})
#   ▲ 路由函数返回 "improve" 或 "end"，path_map 把返回值映射到节点/END
builder.add_edge("improve", "gen")      # 循环：重写后回到生成（或直接回 judge）

graph = builder.compile()
result = graph.invoke({"topic": "递归", "joke": "", "score": 0})
print(f"最终笑话（{result['score']}分）：{result['joke']}")
```

**条件边三要素**：源节点（`judge`）、路由函数（返回节点名/END）、路径映射（可选，`{"improve": "improve"}`）。路由函数返回值就是"下一步去谁"——循环 = 路由回前面的节点。

> **对照 Java**：条件路由 ≈ 状态机里的"guard + transition"；循环 ≈ 状态机回环（S1→S2→S1）。LangGraph 把状态转移写在"图"里，比硬编码 while 更可读、可可视化。

## 12.4 构建 Agent 节点：工具调用循环（衔接第 10 章）

第 10.4 手写 Agent Loop 的循环体，现在变成一个"节点"；**循环由边来表达**：

```python
# 3_tool_agent.py —— LangGraph 版工具调用 Agent
from typing import TypedDict, Annotated, operator
from langgraph.graph import StateGraph, START, END
from langchain_core.messages import HumanMessage, AIMessage, ToolMessage
from langchain_openai import ChatOpenAI

# ① 工具：纯函数（LangGraph 自动转 JSON Schema，原理同第 10.3 章）
def get_weather(city: str) -> str:
    return {"北京": "晴 28°C", "上海": "小雨 25°C"}.get(city, f"{city} 暂无数据")

llm = ChatOpenAI(model="gpt-4o-mini").bind_tools([get_weather])

class AgentState(TypedDict):
    messages: Annotated[list, operator.add]     # 消息累积

def call_model(state: AgentState) -> dict:
    return {"messages": [llm.invoke(state["messages"])]}      # 模型可能带 tool_calls

def call_tools(state: AgentState) -> dict:
    last = state["messages"][-1]
    results = []
    for tc in last.tool_calls:                                 # 执行所有工具请求
        name = tc["name"]
        out = {"get_weather": get_weather}[name](**tc["args"])
        results.append(ToolMessage(content=str(out), tool_call_id=tc["id"]))
    return {"messages": results}

def should_continue(state: AgentState) -> str:
    return "tools" if state["messages"][-1].tool_calls else END   # 还有工具调用→循环

builder = StateGraph(AgentState)
builder.add_node("model", call_model)
builder.add_node("tools", call_tools)
builder.add_edge(START, "model")
builder.add_conditional_edges("model", should_continue, {"tools": "tools", END: END})
builder.add_edge("tools", "model")        # 工具结果回填后继续让模型思考（循环！）

graph = builder.compile()
r = graph.invoke({"messages": [HumanMessage(content="北京和上海天气各是多少？")]})
print(r["messages"][-1].content)
```

**对比第 10.4 自研循环，一眼看懂框架替我们做了什么**：

| 自研版（第 10 章） | LangGraph 版 |
| --- | --- |
| `while` + `max_turns` | `should_continue` 条件边 + 图结构本身防死循环（可配 recursion_limit） |
| `self.messages` 手工维护 | State 的 `Annotated[list, operator.add]` 自动累积 |
| 工具注册表 + 手写 schema | `bind_tools` + 函数自动转 schema |
| 无可视化 | `graph.get_graph().draw_mermaid()` 一行出流程图 |

## 12.5 记忆与持久化：Checkpointer（对标第 10 章 Session）

`graph.invoke` 每次调用状态是独立的。要跨轮记住上下文，用 **checkpointer** 持久化检查点：

```python
# 4_memory.py —— 对话记忆（内存版；生产可换 SQLite/Postgres 检查点）
from typing import TypedDict, Annotated, operator
from langgraph.graph import StateGraph, START, END
from langgraph.checkpoint.memory import MemorySaver
from langchain_core.messages import HumanMessage, AIMessage, SystemMessage
from langchain_openai import ChatOpenAI

class State(TypedDict):
    messages: Annotated[list, operator.add]

llm = ChatOpenAI(model="gpt-4o-mini")
builder = StateGraph(State)

def chat(state: State) -> dict:
    return {"messages": [llm.invoke([SystemMessage(content="你是贴心助手。")] + state["messages"])]}

builder.add_node("chat", chat)
builder.add_edge(START, "chat")
builder.add_edge("chat", END)

checkpointer = MemorySaver()          # 持久化机制：每步保存检查点
graph = builder.compile(checkpointer=checkpointer)

# 关键：thread_id 标识"同一个会话"（对标第 10 章 Session）
c1 = graph.invoke({"messages": [HumanMessage(content="我叫小明")]},
                  config={"configurable": {"thread_id": "user-001"}})
c2 = graph.invoke({"messages": [HumanMessage(content="我叫什么？")]},
                  config={"configurable": {"thread_id": "user-001"}})
print(c2["messages"][-1].content)     # 正确回答"小明"（thread_id 相同→记忆共享）

# 换一个 thread_id 就是全新会话
c3 = graph.invoke({"messages": [HumanMessage(content="我叫什么？")]},
                  config={"configurable": {"thread_id": "user-002"}})
print(c3["messages"][-1].content)     # 不记得 → 说明隔离正确
```

**Checkpointer 带来的超能力**（对比第 10 章手拼 Session）：

1. **跨轮记忆**：`thread_id` 相同即同一会话，状态自动恢复；
2. **断点续跑**：`graph.get_state(config)` 查看任意中间状态，从某节点重放；
3. **人工审批**：`interrupt_before` 在节点前暂停，人工确认后 `Command` 继续。

> **对照 Java**：checkpointer ≈ 流程引擎（Activiti）的任务表持久化——中断/恢复/历史全程可查。LangGraph 的 `thread_id` ≈ 流程实例 ID。

## 12.6 多 Agent 工作流：Supervisor 模式

第 10.7 用 handoffs 做主管-专员，LangGraph 用图更显式：**一个 supervisor 节点 + 多个 worker 节点 + 条件路由循环**。

```python
# 5_supervisor.py —— 多 Agent：主管调度 客服/退款 两个专员
from typing import TypedDict, Annotated, operator
from langgraph.graph import StateGraph, START, END
from langchain_core.messages import HumanMessage, AIMessage, SystemMessage
from langchain_openai import ChatOpenAI

llm = ChatOpenAI(model="gpt-4o-mini")

class State(TypedDict):
    messages: Annotated[list, operator.add]

SYSTEM = "你是客服主管。只输出以下三种之一：general / refund / billing，判断用户意图。"

def supervisor(state: State) -> dict:
    r = llm.invoke([SystemMessage(content=SYSTEM)] + state["messages"])
    return {"messages": [r]}

def refund_agent(state: State) -> dict:
    return {"messages": [AIMessage(content="（退款专员）已记录退款申请，3 个工作日内处理。")]}

def billing_agent(state: State) -> dict:
    return {"messages": [AIMessage(content="（账单专员）发票已开，请查收邮件。")]}

def general_agent(state: State) -> dict:
    return {"messages": [AIMessage(content="（客服）请问还有什么可以帮您？")]}

def route(state: State) -> str:
    label = state["messages"][-1].content.lower()
    for key in ("refund", "billing", "general"):
        if key in label:
            return key
    return "general"

builder = StateGraph(State)
builder.add_node("supervisor", supervisor)
builder.add_node("refund", refund_agent)
builder.add_node("billing", billing_agent)
builder.add_node("general", general_agent)

builder.add_edge(START, "supervisor")
builder.add_conditional_edges("supervisor", route,
                              {"refund": "refund", "billing": "billing", "general": "general"})
builder.add_edge("refund", END); builder.add_edge("billing", END); builder.add_edge("general", END)

graph = builder.compile()
for q in ["我想退款", "给我开发票", "随便聊聊"]:
    r = graph.invoke({"messages": [HumanMessage(content=q)]})
    print(f"问：{q} → {r['messages'][-1].content}")
```

> **对照第 10.7**：handoffs 是"Agent 自己决定交接"，LangGraph 是"路由逻辑显式写成节点/边"。后者更可控、可测试、可可视化——适合**流程固定、需要审计**的场景。

## 12.7 实战：客服 + 工具 + 人工兜底工作流（完整可运行）

综合 12.4~12.6：**主管路由 → 查单工具（RAG 可插）→ 循环 → 无法处理转人工**：

```python
# 6_support_workflow.py —— 生产雏形：意图路由 + 工具循环 + 人工兜底
from typing import TypedDict, Annotated, operator
from langgraph.graph import StateGraph, START, END
from langgraph.checkpoint.memory import MemorySaver
from langchain_core.messages import HumanMessage, AIMessage, ToolMessage, SystemMessage
from langchain_openai import ChatOpenAI

llm = ChatOpenAI(model="gpt-4o-mini")
ORDERS = {"OD2026081901": "已发货", "OD2026081902": "待付款"}

# ① 工具
def query_order(order_id: str) -> str:
    return f"订单 {order_id} 状态：{ORDERS.get(order_id, '未找到')}"

# ② 状态
class State(TypedDict):
    messages: Annotated[list, operator.add]

# ③ 模型节点（带工具）
model = llm.bind_tools([query_order])

def call_model(state: State) -> dict:
    sys = SystemMessage(content=(
        "你是客服。查订单用 query_order 工具；查不到订单直接请用户核对；"
        "涉及退款/投诉等人工事项，输出'转人工'。"))
    return {"messages": [model.invoke([sys] + state["messages"])]}

# ④ 工具节点
def call_tools(state: State) -> dict:
    last = state["messages"][-1]
    return {"messages": [ToolMessage(content=query_order(**t["args"]),
                                     tool_call_id=t["id"]) for t in last.tool_calls]}

# ⑤ 条件路由：工具循环 / 转人工 / 结束
def route(state: State) -> str:
    text = state["messages"][-1].content
    if state["messages"][-1].tool_calls:
        return "tools"
    return "human" if "转人工" in text else END

# ⑥ 人工兜底节点（生产：写工单表 / 通知值班）
def escalate(state: State) -> dict:
    return {"messages": [AIMessage(content="已转人工客服，工单号 #A1024，请稍候。")]}

builder = StateGraph(State)
builder.add_node("model", call_model)
builder.add_node("tools", call_tools)
builder.add_node("human", escalate)
builder.add_edge(START, "model")
builder.add_conditional_edges("model", route, {"tools": "tools", "human": "human", END: END})
builder.add_edge("tools", "model")            # 工具结果回填 → 继续循环

graph = builder.compile(checkpointer=MemorySaver())

def ask(uid: str, text: str):
    r = graph.invoke({"messages": [HumanMessage(content=text)]},
                     config={"configurable": {"thread_id": uid}})
    print(f"问：{text}\n答：{r['messages'][-1].content}\n")

if __name__ == "__main__":
    ask("u1", "我的订单 OD2026081901 状态？")    # 工具循环 → 查询
    ask("u1", "它还没到，我要退款！")             # 语义理解 + 转人工
    ask("u2", "订单一查一个准，请问退款流程")     # 新会话：换 thread 隔离记忆
```

**架构复盘**——这张图覆盖了生产 Agent 的大部分要素：

| 能力 | 实现方式 |
| --- | --- |
| 意图路由 | supervisor 思想（可换成 12.6 独立 supervisor 节点） |
| 工具调用循环 | `model → tools → model` 条件边（第 12.4 模式） |
| 人工兜底 | `human` 节点 + `转人工` 路由（生产接工单系统） |
| 会话隔离 | `thread_id` + checkpointer |
| 可观测 | `graph.get_state` 断点查看、mermaid 可视化 |

## 12.8 练习与面试

### 本章练习

1. 修改 12.2 最小图：加第三个节点 `step_exclaim`，验证线性流水线；
2. 给 12.3 笑话循环加"最大重写 3 次"上限（`recursion_limit` 参数），并验证超限报错；
3. 用 12.4 模式给模型挂两个工具（天气 + 计算器），验证多工具连续调用；
4. 把 12.4 的图用 `graph.get_graph().draw_mermaid()` 导出流程图，观察工具循环结构；
5. 给 12.5 的 checkpointer 换成 `SqliteSaver`（`langgraph-checkpoint-sqlite`），验证重启后记忆仍在；
6. 在 12.6 Supervisor 模式中增加第 4 个专员（如"物流专员"），扩展路由；
7. 把第 11 章 RAG 助手改造成一个"知识库检索"工具节点，接入 12.7 工作流；
8. 选做：用 `interrupt_before=["tools"]` 实现"调用写工具前需人工确认"的审批流。

### 面试题

| 问题 | 要点 |
| --- | --- |
| LangGraph 是什么？核心概念？ | 图编排框架：State（状态）/Node（节点）/Edge（边）/条件边/START-END |
| State 为什么用 Annotated+operator.add？ | 归约器：多节点对同一字段做"追加/合并"而非覆盖 |
| 条件边怎么用？ | add_conditional_edges(源节点, 路由函数, path_map)；路由函数返回节点名/END |
| 循环怎么实现？ | 条件边路由回前置节点（如 model→tools→model） |
| LangGraph 和手写 Agent 循环区别？ | 显式图、状态可持久化、可中断恢复、可可视化 |
| checkpointer 是什么？ | 检查点持久化：跨轮记忆、断点续跑、人工审批（thread_id 隔离会话） |
| 多 Agent 怎么编排？ | Supervisor 模式：主管节点路由 + 专员节点执行 |
| LangGraph 和 openai-agents 的 handoffs 区别？ | 图显式路由 vs Agent 自主交接；前者可控可审计 |
| recursion_limit 是什么？ | 图执行最大步数，防死循环烧 token |
| LangGraph 支持并行吗？ | 支持：多个节点间多条边会并行执行（fan-out/fan-in） |
| 怎么可视化工作流？ | get_graph().draw_mermaid() 导出 mermaid 图 |
| 人工审批怎么实现？ | interrupt_before 暂停 + 恢复继续 |
| 生产用哪个 checkpointer？ | SQLite/Postgres 持久化检查点（进程重启不丢） |
| LangGraph 和 LangChain 关系？ | LangChain 是组件库（模型/工具/文档），LangGraph 是编排框架 |

### 本章小结

- **图思想**（12.1~12.2）：流程 = 状态 + 节点 + 边；三步套路 add_node/add_edge/compile；
- **分支循环**（12.3）：条件边 + 路由函数，笑话生成循环演示；
- **Agent 节点**（12.4）：工具调用循环 = model→tools→model 条件边，对比第 10 章手写循环；
- **记忆持久化**（12.5）：checkpointer + thread_id，跨轮记忆/断点续跑/人工审批；
- **多 Agent**（12.6）：Supervisor 模式显式路由；
- **实战**（12.7）：客服+工具+人工兜底完整工作流。

---

## 🚀 后续进阶方向

- **子图（Subgraph）**：把 12.7 的"工具循环"封装成子图复用；
- **流式输出**：`.astream_events` 实时推送节点执行过程（对接 FastAPI SSE）；
- **LangSmith**：LLM 应用观测平台（trace/评估/数据集）；
- **编排 RAG**：Multi-Agent RAG（主管路由 + 多个领域知识库子图）。

配套：下一章 [13-MCP工具接入.md](./13-MCP工具接入.md)（用 MCP 把工具做成可复用的标准化服务，LangGraph/Agents 通用）｜ [README.md](./README.md)
