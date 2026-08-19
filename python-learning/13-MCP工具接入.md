# 第十三章 MCP 工具接入：给所有 Agent 一根"USB-C"

> 本章目标：解决工具生态的"**碎片化**"——第 10 章的工具是函数、第 12 章的图是节点、别人家的 Agent 又是另一套……**MCP（Model Context Protocol）** 是 AI 工具互操作的开放标准：一次开发工具，Claude、OpenAI Agents、LangGraph、Cursor 全都能用。本章从协议原理讲起，用 **FastMCP** 搭建可运行的 MCP Server，再接入 openai-agents 与 LangGraph，最后把第 10 章客服系统的订单工具"MCP 化"。
>
> 前置知识：第 10 章（Agent 与工具）、第 12 章（LangGraph）。对标 Java：JDBC/ODBC（数据库连接标准）、JNDI/SPI（服务发现）。
>
> 环境准备：`pip install openai openai-agents fastmcp`，设置 `OPENAI_API_KEY`。

## 13.1 MCP 是什么：AI 世界的"USB-C"

### 13.1.1 问题：每个 Agent 一套工具

没有 MCP 之前，同样一个"查订单"能力，要为每个宿主重复实现：

```
Claude Desktop  ──► 专属插件/技能
OpenAI Agents   ──►  @function_tool 函数
LangGraph       ──►  图节点里的函数
Cursor          ──►  .cursor/rules + 工具
自家产品        ──►  再写一套 API
```

**同一个工具 N 个宿主 = N 份开发、N 份维护。** 就像当年每个设备一种充电口。

### 13.1.2 MCP 标准答案：工具做成"服务"，宿主按标准接入

MCP（2024 年底 Anthropic 提出，2025 年起成为事实标准）定义了一套**客户端-服务器协议**：

```
┌────────────┐        ┌────────────┐        ┌─────────────────┐
│  Host 宿主  │◄─JSON-RPC─►│ Client 客户端 │◄──────►│    MCP Server    │
│ (Claude/    │   MCP 协议  │ (SDK 内置)    │  tools/资源/提示词 │  (你的业务能力)  │
│  Agents/     │            │              │                    │  e.g. 查订单    │
│  LangGraph)  │            │              │                    │                 │
└────────────┘        └────────────┘        └─────────────────┘
```

| 角色 | 干什么 | 类比 |
| --- | --- | --- |
| **Host** | 面向用户的 AI 应用（Claude Desktop、IDE、Agent） | 应用层 |
| **Client** | Host 与 Server 的桥梁（SDK 内置，你基本不用碰） | JDBC Driver |
| **Server** | 暴露"能力"给 AI 用的服务（你主要写它） | 数据库/服务端 |

**一次开发、处处可用**：写一个 MCP Server（查订单），Claude Desktop、openai-agents、LangGraph、Cursor 全部能直接调用——就像 USB-C 线接所有设备。

> **对照 Java**：MCP 就像 JDBC——上层应用（Host）通过统一接口（协议）使用任意实现（Server），换数据库不用改应用代码。MCP 把"工具"标准化成了 JDBC 对"数据库"做的事。

## 13.2 核心概念：Tools / Resources / Prompts

MCP Server 对外暴露三类能力：

| 能力 | 是什么 | 类比 |
| --- | --- | --- |
| **Tools（工具）** | 可被模型调用的**函数**（可带副作用：查、写、发） | 第 10 章 `@function_tool` |
| **Resources（资源）** | 只读**数据/文件**（文档、配置、数据库视图） | REST 的 GET 接口 |
| **Prompts（提示词）** | 可复用的**提示模板**（带参数） | 模板引擎 |

选择口诀：**要"做事"用 Tools，要"取数"用 Resources，要"引导"用 Prompts**。

### 13.2.1 传输方式：进程内 / 远程

| 传输 | 场景 | 说明 |
| --- | --- | --- |
| **stdio** | 本地（Server 是子进程） | 最简单：`npx -y xxx` 或 `python server.py` 拉起 |
| **Streamable HTTP / SSE** | 远程（Server 是 HTTP 服务） | 跨机器、跨语言，生产主流 |

## 13.3 用 FastMCP 搭建第一个 MCP Server

FastMCP 是"写 MCP Server 最爽的 Python 框架"——装饰器风格（对标第 10 章 `@function_tool` 与第 8 章 click）。

```python
# server.py —— 第一个 MCP Server：天气 + 订单 两个工具
from fastmcp import FastMCP
from pydantic import BaseModel, Field

mcp = FastMCP("support-tools")          # Server 名字

# ── Tools：可被 Agent 调用的函数（类型注解自动生成 schema，同第 10 章）──
@mcp.tool()
def get_weather(city: str) -> str:
    """查询城市当前天气"""
    return {"北京": "晴 28°C", "上海": "小雨 25°C"}.get(city, f"{city} 暂无数据")

class OrderId(BaseModel):
    order_id: str = Field(..., description="订单号，形如 OD2026xxxx")

@mcp.tool()
def query_order(q: OrderId) -> str:
    """按订单号查询订单状态"""
    table = {"OD2026081901": "已发货", "OD2026081902": "待付款"}
    return f"订单 {q.order_id}：{table.get(q.order_id, '未找到')}"

# ── Resources：只读数据（模型可读取上下文）──
@mcp.resource("config://return-policy")
def return_policy() -> str:
    """退货政策（字符串资源）"""
    return "商品已签收且金额≤200 元可退款。"

# ── Prompts：可复用提示模板 ──
@mcp.prompt()
def order_help(order_id: str) -> str:
    """生成查订单引导提示"""
    return f"用户需要查询订单 {order_id}，请用 query_order 工具。"

if __name__ == "__main__":
    mcp.run()        # 默认 stdio 传输；mcp.run(transport="streamable-http") 可开 HTTP
```

运行与自测：

```bash
python server.py                       # 启动（stdio 模式，等待宿主连接）

# 快速自测（FastMCP 内置调试客户端）
mcp dev server.py                      # 交互式 MCP Inspector（可视化调试台）
```

> **对照第 10 章**：`@mcp.tool()` ≈ `@function_tool`，但前者是**独立服务**——不依赖任何 Agent 框架，宿主换成谁都行。

## 13.4 客户端接入：让 Agent 用上 MCP Server

### 13.4.1 openai-agents 接入（第 10 章框架）

```python
# 1_agents_mcp.py —— OpenAI Agents SDK 接入 MCP Server
import asyncio
from agents import Agent, Runner
from agents.mcp import MCPServerStdio

async def main():
    # stdio 方式拉起刚才的 server.py（本地子进程）
    server = MCPServerStdio(
        command="python",
        args=["server.py"],
        cache_tools_list=True,          # 缓存工具清单，避免每次启动都枚举
    )
    agent = Agent(
        name="客服助手",
        instructions="查订单用工具；问天气也能查。",
        mcp_servers=[server],           # 工具自动注入！
    )
    result = await Runner.run(agent, "帮我查 OD2026081901 和北京的天气")
    print(result.final_output)
    await server.cleanup()

asyncio.run(main())
```

**接入后发生了什么**：SDK 自动发现 Server 的 tools（`get_weather`/`query_order`）→ 生成 schema → 进 Agent 的工具列表 → 模型按需调用，工具执行由 SDK 转发给 Server。**Agent 代码零感知 MCP**——`mcp_servers=[server]` 一行接入。

远程 Server（HTTP）：

```python
from agents.mcp import MCPServerSse
server = MCPServerSse(endpoint="https://example.com/mcp")   # 远程工具即插即用
```

### 13.4.2 LangGraph 接入（第 12 章框架）

```python
# 2_langgraph_mcp.py —— LangGraph 接入 MCP Server
from langchain_mcp_adapters.tools import load_mcp_tools
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client

async def get_tools():
    params = StdioServerParameters(command="python", args=["server.py"])
    async with stdio_client(params) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()
            return await load_mcp_tools(session)     # MCP 工具 → LangChain 工具

# 拿到的 tools 直接进第 12.4 的 bind_tools / 图节点即可
tools = asyncio.run(get_tools())
```

### 13.4.3 任意 Python 客户端手动调用（理解协议本质）

```python
# 3_raw_client.py —— 不依赖框架，看穿协议：tools/list + tools/call
import asyncio
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client

async def main():
    params = StdioServerParameters(command="python", args=["server.py"])
    async with stdio_client(params) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()
            tools = await session.list_tools()                 # 协议：tools/list
            print("Server 暴露的工具：", [t.name for t in tools.tools])
            result = await session.call_tool(                  # 协议：tools/call
                "query_order", {"order_id": "OD2026081901"}
            )
            print("调用结果：", result.content)

asyncio.run(main())
```

> 协议本质就两个 RPC：`tools/list`（宿主问"你有什么能力"）与 `tools/call`（宿主说"调用这个"）——JSON-RPC 2.0 之上，就是这么简单。理解这两行，MCP 就不再神秘。

## 13.5 实战：把客服系统工具 MCP 化（复用第 10 章）

第 10.8 客服助手的 `query_order` 是函数，现在升级为 **MCP Server**——同一份能力，OpenAI Agents、LangGraph、Claude Desktop 都能用：

```python
# server_order.py —— 客服订单工具 MCP Server（生产可接 SQLAlchemy/第 6 章数据库）
from fastmcp import FastMCP
from pydantic import BaseModel, Field

mcp = FastMCP("order-service")

ORDERS = {
    "OD2026081901": {"status": "已发货", "eta": "2026-08-21", "amount": 129.0},
    "OD2026081902": {"status": "待付款", "eta": None, "amount": 399.0},
    "OD2026081903": {"status": "已签收", "eta": "2026-08-17", "amount": 55.0},
}

class OrderId(BaseModel):
    order_id: str = Field(..., description="订单号，形如 OD2026xxxx")

@mcp.tool()
def query_order(q: OrderId) -> str:
    """按订单号查询状态/预计送达/金额（只读）"""
    return str(ORDERS.get(q.order_id, f"未找到订单 {q.order_id}"))

@mcp.tool()
def check_refund_eligibility(q: OrderId) -> str:
    """检查订单是否满足退款条件（已签收且金额≤200）"""
    o = ORDERS.get(q.order_id)
    if not o:
        return "订单不存在"
    ok = o["status"] == "已签收" and o["amount"] <= 200
    return f"可退款：{ok}" + (f"（金额 {o['amount']} 元）" if ok else "")

if __name__ == "__main__":
    mcp.run()
```

```python
# client_agent.py —— 用 OpenAI Agents 消费上面的 MCP Server
import asyncio
from agents import Agent, Runner
from agents.mcp import MCPServerStdio

async def main():
    server = MCPServerStdio(command="python", args=["server_order.py"])
    agent = Agent(
        name="客服主管",
        instructions=(
            "你是客服：查订单用 query_order；"
            "用户要求退款时先用 check_refund_eligibility 判断，符合条件再受理。"
        ),
        mcp_servers=[server],
    )
    result = await Runner.run(agent, "订单 OD2026081903 想退款，帮我查一下能不能退")
    print(result.final_output)
    await server.cleanup()

asyncio.run(main())
```

**架构收益**（对比第 10.8 函数版）：

| 维度 | 第 10 章函数版 | 本章 MCP 版 |
| --- | --- | --- |
| 复用性 | 只能给这一个 Agent | 任何 MCP 宿主（Agents/LangGraph/Claude/Cursor） |
| 部署 | 函数 = 代码内嵌 | 独立进程/服务，可单独部署升级 |
| 边界 | 无 | Server 是明确信任边界（权限/审计/鉴权） |
| 语言 | Python 专属 | 协议跨语言（Java 写的 Server Python 也能调） |

## 13.6 生态与安全

### 13.6.1 现成 Server：不必什么都自己写

MCP 生态有大量现成 Server（GitHub、数据库、浏览器、Slack…），`npx`/`uvx` 一行拉起：

```bash
# 例：文件系统 MCP Server（Claude Desktop 常用）
npx -y @modelcontextprotocol/server-filesystem D:\data

# 例：GitHub MCP Server
docker run -e GITHUB_PERSONAL_ACCESS_TOKEN=xxx ghcr.io/github/github-mcp-server
```

选型先搜 `mcp.so` / `glama.ai` 等注册表，**能不写就不写**。

### 13.6.2 安全：MCP 是新的攻击面

MCP 让 AI 有了"手"，也让攻击者有了新入口：

| 风险 | 缓解 |
| --- | --- |
| **工具越权** | Server 内做鉴权/授权；写操作（删除/转账）单独高风险工具，宿主侧人工确认 |
| **Prompt 注入跨 Server** | 工具结果里的恶意指令 → Server 只回结构化数据，不回指令文本 |
| **远程 Server 不可信** | 只连可信 endpoint；用 OAuth 2.1 鉴权（MCP 规范支持） |
| **敏感数据泄露** | Resources 按需暴露、日志脱敏；Server 与业务系统之间做白名单 |

> **核心原则（呼应第 10.9）**：MCP Server = **能力边界 = 安全边界**。把 Server 当成对外 API 一样设计：参数校验（Pydantic）、权限最小化、写操作显式化、全量审计日志。

### 13.6.3 工程化（衔接第 9 章）

- **测试**：Server 的工具是纯函数，pytest 直接单测；集成测试用 `mcp dev` 自测；
- **发布**：FastMCP Server 用 `pyproject.toml`（第 9 章）打包成 CLI 包，`uvx`/`pipx` 一行安装启动；
- **观测**：Server 侧打日志（第 3 章 logging）+ 指标；宿主侧结合第 10 章 Tracing；
- **部署**：stdio 随宿主进程；远程用 Docker + Streamable HTTP，走网关统一鉴权。

## 13.7 练习与面试

### 本章练习

1. 在 server.py 里新增一个"计算运费"工具（根据金额/地区返回运费），并用 `mcp dev` 自测；
2. 用 13.4.3 原始客户端手动调用你新加的工具，观察 `tools/list` 与 `tools/call` 报文；
3. 把 13.5 的 order Server 接入 LangGraph（13.4.2），替换第 12.7 实战里的本地函数工具；
4. 为 order Server 增加"开发票"写工具，并在 Agent 指令中要求"调用前向用户二次确认"；
5. 用 FastMCP 的 `transport="streamable-http"` 启动远程 Server，再用 `MCPServerSse` 远程接入；
6. 将 13.5 的 MCP Server 打成可安装包（第 9 章 pyproject.toml），验证 `uvx run server_order` 可直接启动；
7. 选做：给 order Server 加 OAuth 鉴权（或简单 token），验证未授权调用被拒。

### 面试题

| 问题 | 要点 |
| --- | --- |
| MCP 是什么？ | Model Context Protocol：AI 工具互操作开放标准，Host/Client/Server 三层 |
| MCP 解决什么问题？ | 工具碎片化：一次开发，Claude/OpenAI Agents/LangGraph 等全部可用 |
| MCP 的三大能力？ | Tools（可调用函数）、Resources（只读数据）、Prompts（提示模板） |
| MCP 传输方式？ | stdio（本地子进程）、Streamable HTTP/SSE（远程） |
| 协议底层是什么？ | JSON-RPC 2.0；核心 RPC：tools/list、tools/call |
| 怎么用 FastMCP 写 Server？ | @mcp.tool()/@mcp.resource()/@mcp.prompt() 装饰器，类型注解自动 schema |
| openai-agents 怎么接入？ | agents.mcp.MCPServerStdio/SSE + Agent(mcp_servers=[...]) |
| LangGraph 怎么接入？ | langchain_mcp_adapters.load_mcp_tools(session) |
| MCP 和 function calling 关系？ | function calling 是"机制"；MCP 是"工具的标准分发协议"，函数调用在 Server 里执行 |
| MCP 和 JDBC 类比？ | 统一接口（协议）连接任意实现（Server），换实现不改宿主代码 |
| MCP Server 安全注意什么？ | 鉴权、工具权限最小化、写操作确认、Prompt 注入防护、审计 |
| 什么时候不用 MCP？ | 工具只给一个 Agent 用、无复用需求时，普通函数就够 |
| 怎么找现成 MCP Server？ | mcp.so / glama.ai 注册表；npx/docker 一行拉起 |
| MCP Server 怎么部署？ | 本地 stdio；生产远程 Docker + Streamable HTTP + 网关 |

### 本章小结

- **协议定位**（13.1）：AI 的 USB-C；一次开发处处可用；对标 JDBC；
- **三大能力**（13.2）：Tools/Resources/Prompts；stdio 与远程传输；
- **FastMCP**（13.3）：装饰器一行一个工具，`mcp dev` 可视化自测；
- **三端接入**（13.4）：openai-agents（mcp_servers）、LangGraph（load_mcp_tools）、原始客户端（tools/list+call）；
- **实战**（13.5）：客服订单工具 MCP 化，复用第 10 章代码；
- **生态安全**（13.6）：现成 Server 复用、鉴权/权限/注入防护、工程化发布。

---

## 🚀 后续进阶方向

- **MCP Gateway**：统一入口代理多个 Server（路由、鉴权、限流）；
- **Schema 注册中心**：工具/资源的版本管理与发现；
- **语音/多模态 MCP**：把语音识别、图像理解也做成工具；
- **与 RAG 结合**：把第 11 章检索能力封装成 MCP Resource/Tool，全局复用。

配套：下一章 [14-机器学习.md](./14-机器学习.md)（从"调模型 API"升级为"自己训练模型"）｜ [README.md](./README.md) ｜ 至此 Python 体系共 **16 章**：9 章主线 + 7 章 AI/数据扩展（Agent / RAG / 编排 / MCP / 机器学习 / 语音多模态 / 评测）。
