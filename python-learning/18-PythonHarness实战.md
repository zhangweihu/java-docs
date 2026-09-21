# 第十八章 Python Harness 实战：为代码与 Agent 装上"质量护栏"

> 本章目标：把前 17 章的"能写 Python / 能写 Agent"升级为"**专业地测试 Python / Agent**"。**Harness** 是工程语境里的"质量护栏集合"——本章把它拆成两条线：
> 1. **测试 Harness**（传统）：pytest 全家桶 + 覆盖率 + 变异测试 + 属性测试 + CI；
> 2. **AI Agent Harness**（新）：LLM-as-Judge + Golden Set + 工具调用断言 + 成本延迟断言 + 端到端可观测。
>
> 前置知识：第 9 章（pytest 工程化）、第 10~17 章（Agent / RAG / LangGraph / AutoGen / MCP）。对标 java-learning 第 83 章《软件测试 Harness 实战》、第 84 章《AI Agent Harness 实战》。
>
> 环境准备：`pip install pytest pytest-cov pytest-mock hypothesis mutpytest deepeval pytest-asyncio langfuse`，设置 `OPENAI_API_KEY`。

---

## 18.1 什么是 Harness

### 18.1.1 工程语境下的定义

> **Harness**（测试套具 / 测试框架）= 一组让"被测对象"能在**受控、可观测、可重复**条件下运行的设施。

它不是"一个测试"，而是"测试的支撑系统"：

```
被测代码 / Agent
    │
    ├── Fixture（夹具）：构造/清理环境
    ├── Runner（运行器）：调度测试用例
    ├── Assertion（断言器）：判断结果
    ├── Reporter（报告器）：呈现结果（人/CI 看板）
    └── Driver（驱动器）：触发运行（CI / 本地 / 定时）
```

### 18.1.2 测试 Harness vs AI Agent Harness

| 维度 | 测试 Harness | AI Agent Harness |
| --- | --- | --- |
| **被测对象** | 函数、API、模块 | Agent、Workflow、RAG、工具 |
| **断言方式** | `assert x == 3` | LLM-as-Judge / 软断言 / 阈值 |
| **环境** | 数据库、文件、网络 | + LLM endpoint / 向量库 / MCP 服务 |
| **不确定性** | 低（相同输入相同输出） | 高（概率性输出） |
| **核心指标** | 覆盖率、变异杀死率 | 任务成功率、忠实度、token 成本、p95 延迟 |
| **失败模式** | 抛异常 / 返错值 | 静默出错（答非所问也返回 200） |

> **本章两条线都讲**：18.2~18.7 是测试 Harness，18.8~18.12 是 AI Agent Harness，18.13 把两者合成 CI 一键跑通。

---

## 18.2 测试 Harness 组件全景

```
pytest_harness/
├── conftest.py                 # 全局 fixture
├── fixtures/                   # 可复用夹具
│   ├── db.py                   # 测试库
│   └── api.py                  # mock HTTP
├── tests/
│   ├── unit/                   # 单元测试（快）
│   ├── integration/            # 集成测试（用真实依赖）
│   └── e2e/                    # 端到端（慢）
├── property/                   # 属性测试（Hypothesis）
├── mutation/                   # 变异测试（mutmut）
├── eval/                       # AI 评测
│   ├── golden.jsonl
│   └── judge_prompts/
├── pytest.ini
├── pyproject.toml
└── .github/workflows/ci.yml
```

下面从最常用的 pytest fixture 开始，逐层搭。

---

## 18.3 pytest Fixture：可复用的"夹具"

### 18.3.1 函数级 vs 模块级 vs 会话级

```python
# conftest.py —— 三种作用域的 fixture
import pytest

@pytest.fixture                 # 函数级：每个测试函数都跑一遍 setup/teardown
def db_conn():
    conn = create_connection("test.db")
    yield conn                  # 测试运行到这里
    conn.close()

@pytest.fixture(scope="module") # 模块级：整文件共享（重型资源）
def docker_container():
    container = start_postgres()
    yield container
    container.stop()

@pytest.fixture(scope="session") # 会话级：整个测试会话一次
def model_client():
    return OpenAIChatCompletionClient(model="gpt-4o-mini")
```

### 18.3.2 Fixture 组合：`@pytest.fixture` 嵌套

```python
@pytest.fixture
def user(db_conn):
    db_conn.execute("INSERT INTO users(name) VALUES('alice')")
    return db_conn.query("SELECT * FROM users WHERE name='alice'")[0]

@pytest.fixture
def order(user):
    return create_order(user_id=user["id"], amount=100)

def test_pay_order(order, db_conn):
    pay(order)
    assert db_conn.query("SELECT status FROM orders WHERE id=?", order["id"])[0]["status"] == "PAID"
```

`test_pay_order` 接收 `order`，pytest 自动解析依赖：`order → user → db_conn`，**按依赖图自动实例化**。

### 18.3.3 内置 fixture：`tmp_path`、`monkeypatch`、`capsys`

```python
def test_write_file(tmp_path):                  # 自动建临时目录
    f = tmp_path / "out.txt"
    write_to(f, "hello")
    assert f.read_text() == "hello"

def test_env(monkeypatch):                       # 临时改环境变量
    monkeypatch.setenv("API_KEY", "test-key")
    assert get_api_key() == "test-key"

def test_print(capsys):                          # 捕获 stdout/stderr
    print("hello")
    captured = capsys.readouterr()
    assert captured.out == "hello\n"
```

### 18.3.4 `conftest.py` 的层级与共享

pytest 自动向上搜索 `conftest.py`，形成"作用域继承"：

```
project/
├── conftest.py              # 整个项目共享
├── tests/
│   ├── conftest.py          # 整个 tests 目录共享
│   ├── unit/
│   │   ├── conftest.py      # 仅 unit 目录可见
│   │   └── test_x.py
│   └── integration/
│       └── test_y.py        # 可见:project + tests + integration
```

> **惯例**：`tests/conftest.py` 放共享 fixture；子目录 `conftest.py` 放专用 fixture（数据库、HTTP mock）。

---

## 18.4 参数化与 Mock：消除重复与隔离依赖

### 18.4.1 `parametrize`：一份代码，N 份用例

```python
# test_calc.py —— 数据驱动
import pytest
from myapp.calc import discount

@pytest.mark.parametrize(
    "amount, vip, expected",
    [
        (100,   False, 100),     # 普通用户无折扣
        (100,   True,   80),     # VIP 8 折
        (200,   True,   160),
        (0,     False,  0),      # 边界
        (-1,    False,  0),      # 非法输入兜底
    ],
)
def test_discount(amount, vip, expected):
    assert discount(amount, vip) == expected
```

5 组数据 → 5 个独立测试用例，失败时报错清楚指明哪组挂了。

### 18.4.2 `pytest-mock`：优雅地 Mock

```python
# test_api.py —— mock 外部 HTTP
from pytest_mock import MockerFixture
import httpx

def test_fetch_user_success(mocker: MockerFixture):
    fake_resp = mocker.Mock(status_code=200, json=lambda: {"id": 1, "name": "alice"})
    mocker.patch("httpx.get", return_value=fake_resp)

    user = fetch_user(1)

    assert user["name"] == "alice"
    httpx.get.assert_called_once_with("https://api/users/1", timeout=5)

def test_fetch_user_timeout(mocker: MockerFixture):
    mocker.patch("httpx.get", side_effect=httpx.TimeoutException)

    with pytest.raises(UserFetchError):
        fetch_user(1)
```

**两个常用模式**：

- `mocker.patch("模块.函数", return_value=...)` —— 全局替换；
- `mocker.patch.object(类, "方法", ...)` —— 局部替换；
- `side_effect=[a, b, raise]` —— 多次调用返回不同结果，最后一次抛异常。

### 18.4.3 用 `respx` mock 整个 HTTPClient

```python
# test_api_v2.py —— respx 拦截 httpx.AsyncClient
import respx
from httpx import Response

@respx.mock
async def test_async_fetch():
    respx.get("https://api/users/1").mock(
        return_value=Response(200, json={"id": 1, "name": "alice"})
    )
    user = await async_fetch_user(1)
    assert user["name"] == "alice"
```

> 对比 `pytest-mock` 直接 patch：`respx` 更"真实"——可以模拟延迟、状态码、HTTP 异常。

---

## 18.5 覆盖率：从"写了测试"到"测全了"

### 18.5.1 `pytest-cov` 基础

```bash
pytest --cov=myapp --cov-report=term-missing
# 输出：
# Name                  Stmts   Miss  Cover   Missing
# -------------------------------------------------
# myapp/__init__.py          1      0   100%
# myapp/calc.py              8      0   100%
# myapp/api.py              20      2    90%   45, 67
# -------------------------------------------------
# TOTAL                      29      2    93%
```

### 18.5.2 覆盖率"红线"配置

```toml
# pyproject.toml
[tool.coverage.run]
branch = true             # 分支覆盖（if 的两个分支都要跑到）
source = ["myapp"]
omit = ["*/tests/*", "*/__init__.py"]

[tool.coverage.report]
exclude_lines = [
    "pragma: no cover",
    "raise NotImplementedError",
    "if __name__ == .__main__.:",
]
fail_under = 90            # 低于 90% CI 失败
show_missing = true
```

### 18.5.3 增量覆盖率：PR 只看"改了什么"

```bash
pytest --cov=myapp --cov-report=term --cov-report=xml
# 把 coverage.xml 上传到 Codecov / Coveralls，配合 diff 算法只显示新代码的覆盖率
```

### 18.5.4 覆盖率的"盲区"

**覆盖率 ≠ 测试质量**。100% 行覆盖的代码，可能断言全错：

```python
def divide(a, b):
    if b == 0:
        return None       # ← 100% 覆盖
    return a / b

# 测试
def test_divide():
    assert divide(10, 2) is not None   # ← 看似有断言，实际只验证了"非空"
```

**对策**：下一节的**变异测试**专门解决这种问题。

---

## 18.6 变异测试：用"故意搞坏"检验测试强度

### 18.6.1 原理

变异测试（Mutation Testing）会**自动把代码改坏**（变异），看你的测试能不能发现：

```python
# 原始代码
def discount(amount, vip):
    if vip:
        return amount * 0.8
    return amount

# 变异 1:把 * 0.8 改成 * 0.9
def discount_MUTANT_1(amount, vip):
    if vip:
        return amount * 0.9   # ← 改坏
    return amount

# 变异 2:把 if vip 改成 if not vip
def discount_MUTANT_2(amount, vip):
    if not vip:               # ← 改坏
        return amount * 0.8
    return amount
```

如果**变异后测试还能通过**，说明你的测试"太弱"——变异"存活"了。

### 18.6.2 `mutmut` 实操

```bash
pip install mutmut

# 1. 生成变异
mutmut run --paths-to-mutate=myapp/calc.py

# 2. 查看结果
mutmut results
# <Result of 'myapp.calc.discount'>
# - mutant 1: SURVIVED  ← 你的测试没抓到！
# - mutant 2: KILLED     ← 你的测试抓到了

# 3. 增强测试
def test_discount_vip_gets_20_percent_off():
    assert discount(100, vip=True) == 80   # 精确断言：80 而不是 "is not None"

# 4. 再跑
mutmut run
# mutant 1: KILLED  ← 现在抓到了
```

### 18.6.3 变异测试的工程取舍

| 维度 | 建议 |
| --- | --- |
| 跑哪些文件 | **核心业务逻辑**（订单、计费、权限），不跑 UI/胶水代码 |
| 跑多快 | 局部跑（10 分钟内）；不要全量（动辄数小时） |
| 何时跑 | PR 合并前 / 每天定时，不放 PR 必跑流水线 |
| 目标指标 | **变异杀死率 ≥ 70%**（行业基线） |

---

## 18.7 属性测试：`Hypothesis` 帮你找边界

单元测试给固定输入；**属性测试给随机输入，看不变量是否始终成立**。

### 18.7.1 经典示例：排序函数

```python
# test_sort_property.py —— 排序的三个属性
from hypothesis import given, strategies as st

@given(st.lists(st.integers, min_size=0, max_size=100))
def test_sort_is_idempotent(xs):
    """两次排序结果相同"""
    assert sorted(sorted(xs)) == sorted(xs)

@given(st.lists(st.integers, min_size=1, max_size=100))
def test_sort_preserves_length(xs):
    """排序后长度不变"""
    assert len(sorted(xs)) == len(xs)

@given(st.lists(st.integers, min_size=0, max_size=100))
def test_sort_output_is_ordered(xs):
    """排序后是单调递增"""
    result = sorted(xs)
    assert all(result[i] <= result[i+1] for i in range(len(result)-1))
```

`Hypothesis` 会自动**收缩**（shrink）失败输入到最小反例：

```
Falsifying example: test_sort_preserves_length(
    xs=[0, 0],
)
```

### 18.7.2 自定义策略

```python
from hypothesis import strategies as st
from myapp.models import User

user_strategy = st.builds(
    User,
    id=st.integers(min_value=1),
    name=st.text(min_size=1, max_size=50),
    age=st.integers(min_value=0, max_value=150),
)

@given(user_strategy, st.integers(min_value=0))
def test_user_age_invariant(user, days):
    assert user.age + days >= 0
```

### 18.7.3 与单元测试的关系

| 单元测试 | 属性测试 |
| --- | --- |
| 验证特定输入 → 特定输出 | 验证任意输入 → 某个不变量 |
| 适合业务逻辑明确路径 | 适合发现未知 bug / 边界 |
| 慢的、精确的 | 快的、广覆盖 |
| 必须有 | **强烈推荐补充** |

---

## 18.8 AI Agent Harness：把第 16 章的评测体系落到代码

第 16 章讲了 Agent 评测金字塔、Golden Set、LLM-as-Judge；这里把它们**工程化**成可跑测试。

### 18.8.1 AI Agent Harness 的核心断言类型

```python
# 1. 工具调用断言：调对了没？
assert_called_with(tool="search_web", args={"query": "2026 RAG"})
assert_call_count(tool="search_web", max=2)

# 2. 内容断言：回答里有/没有某个事实
assert_contains(answer, "向量检索")
assert_not_contains(answer, "根据我的知识")   # 反幻觉

# 3. 成本断言：token 不能爆
assert token_count(response) < 2000

# 4. 风格断言：语气符合人设
assert_tone(answer, tone="professional")

# 5. 结构断言：JSON Schema 校验
assert_matches_schema(answer, AnswerSchema)
```

### 18.8.2 最小可跑的 Agent Harness

```python
# test_agent_harness.py —— 给一个客服 Agent 装 Harness
import pytest
from openai import OpenAI
from myagents.cs_agent import customer_service_agent

client = OpenAI()
GOLDEN_CASES = [
    {
        "id": "cs-001",
        "input": "我的订单 OD2026081901 到哪了？",
        "must_contain": ["OD2026081901", "已发货"],   # 必含
        "must_not_contain": ["未知"],                 # 必不含
        "max_tokens": 500,
    },
    {
        "id": "cs-002",
        "input": "我想退款",
        "must_call_tool": "refund_flow",              # 必调工具
        "max_rounds": 5,
    },
]

@pytest.mark.parametrize("case", GOLDEN_CASES, ids=[c["id"] for c in GOLDEN_CASES])
def test_cs_agent(case):
    response = customer_service_agent.run(
        input=case["input"],
        max_tokens=case.get("max_tokens", 1000),
        max_rounds=case.get("max_rounds", 10),
    )

    answer = response.final_answer

    # 1. 内容断言
    for keyword in case.get("must_contain", []):
        assert keyword in answer, f"[{case['id']}] 缺少关键词: {keyword}"

    for keyword in case.get("must_not_contain", []):
        assert keyword not in answer, f"[{case['id']}] 不该出现的词: {keyword}"

    # 2. 工具调用断言
    if "must_call_tool" in case:
        tool_calls = [t.name for t in response.tool_calls]
        assert case["must_call_tool"] in tool_calls, \
            f"[{case['id']}] 未调用工具 {case['must_call_tool']}，实际: {tool_calls}"

    # 3. 成本断言
    assert response.token_count <= case.get("max_tokens", 2000), \
        f"[{case['id']}] token 超限: {response.token_count}"
```

### 18.8.3 用 JSONL 管理 Golden Set

把上面的列表外置到 `eval/golden.jsonl`：

```jsonl
{"id":"cs-001","input":"我的订单 OD2026081901 到哪了？","must_contain":["OD2026081901","已发货"],"must_not_contain":["未知"],"max_tokens":500}
{"id":"cs-002","input":"我想退款","must_call_tool":"refund_flow","max_rounds":5}
{"id":"cs-003","input":"你们的营业时间？","must_contain":["9:00","18:00"]}
```

加载：

```python
import json
def load_golden(path="eval/golden.jsonl"):
    with open(path) as f:
        return [json.loads(line) for line in f if line.strip()]

GOLDEN_CASES = load_golden()
```

> **好处**：非工程师（产品/QA）也能直接改 `golden.jsonl` 补充用例，**评测与代码解耦**。

---

## 18.9 LLM-as-Judge：用大模型当裁判

第 16 章讲过原理，这里把它做成 pytest fixture：

```python
# conftest.py —— 提供 judge fixture
import pytest
from openai import OpenAI

@pytest.fixture(scope="session")
def judge_client():
    return OpenAI()

JUDGE_PROMPT = """你是严格的客服质量评审，请按以下维度对 Agent 回答打分（1~10）：

【输入】{input}
【Agent 回答】{answer}

评分维度：
1. 准确性（1~10）：信息是否正确、有无幻觉
2. 相关性（1~10）：是否答非所问
3. 简洁性（1~10）：是否冗余
4. 礼貌性（1~10）：语气是否专业

只返回 JSON，格式：
{{"accuracy": N, "relevance": N, "conciseness": N, "politeness": N, "total": N, "comment": "..."}}
"""

def llm_judge(client, input_text: str, answer: str) -> dict:
    import json
    resp = client.chat.completions.create(
        model="gpt-4o",
        messages=[{"role": "user", "content": JUDGE_PROMPT.format(input=input_text, answer=answer)}],
        response_format={"type": "json_object"},
    )
    return json.loads(resp.choices[0].message.content)
```

测试用例：

```python
@pytest.mark.parametrize("case", GOLDEN_CASES, ids=[c["id"] for c in GOLDEN_CASES])
def test_cs_agent_with_judge(case, judge_client):
    response = customer_service_agent.run(input=case["input"])
    scores = llm_judge(judge_client, case["input"], response.final_answer)

    # 设定阈值
    assert scores["accuracy"]   >= 8, f"准确性不达标: {scores}"
    assert scores["relevance"]  >= 8, f"相关性不达标: {scores}"
    assert scores["total"]      >= 32, f"总分不达标: {scores}"
```

**注意 LLM-as-Judge 的偏差**（第 16 章展开过）：

- **位置偏差**：倾向选第一个回答；
- **冗长偏差**：倾向选更长的回答；
- **自我偏好**：用同款模型时打分偏高。

**缓解**：用更**强**的模型当裁判、交换回答顺序、加 few-shot 校准。

---

## 18.10 工具调用 Harness：mock LLM 让 Agent 测试稳定

Agent 测试最大的痛点：**直接调 LLM 慢 + 贵 + 不稳定**。解法：用 **mock LLM 客户端**。

### 18.10.1 录制回放（最稳）

先用真实 LLM 跑一遍，把请求/响应存盘；测试时回放：

```python
# conftest.py —— 用 vcrpy 录制 LLM HTTP 请求
import vcr

@pytest.fixture
def vcr_cassette(tmp_path):
    return vcr.VCR(
        cassette_library_dir=str(tmp_path / "cassettes"),
        record_mode="once",           # 第一次录制，后续回放
        match_on=["method", "scheme", "host", "port", "path", "query"],
    ).use_cassette("cs_agent.yaml")

def test_cs_agent_deterministic(vcr_cassette):
    response = customer_service_agent.run(input="...")
    assert "OD2026081901" in response.final_answer
    # 第二次跑（CI 里）直接读 cassette，不调真实 LLM
```

### 18.10.2 用 `pytest-aiomock` / `respx` 拦截 OpenAI SDK

```python
import respx
from openai import OpenAI

@respx.mock
def test_agent_with_mock_llm():
    # mock Chat Completion endpoint
    respx.post("https://api.openai.com/v1/chat/completions").mock(
        return_value=Response(200, json={
            "choices": [{"message": {"role": "assistant", "content": "已发货"}}],
            "usage": {"total_tokens": 50},
        })
    )

    client = OpenAI(api_key="test")
    resp = client.chat.completions.create(...)
    assert resp.choices[0].message.content == "已发货"
```

### 18.10.3 何时用哪种？

| 场景 | 推荐 |
| --- | --- |
| **单元测试工具/路由逻辑** | `respx` mock 全部 |
| **集成测试 Agent 全链路** | `vcrpy` 录制回放 |
| **E2E 真实质量评测** | 真 LLM（每天定时跑，控制成本） |
| **CI PR 必跑** | `respx` / `vcrpy`（快、稳、免费） |
| **CI 每日回归** | 真 LLM（慢、贵，但反映真实能力） |

---

## 18.11 可观测：把 Langfuse / Phoenix 接到 Harness

测试断言告诉你"过没过"；**可观测告诉你"为什么没"**。

### 18.11.1 Langfuse 接入（最简）

```python
# conftest.py —— 全局启用 Langfuse
from langfuse import Langfuse
import os

@pytest.fixture(scope="session", autouse=True)
def langfuse_setup():
    os.environ["LANGFUSE_PUBLIC_KEY"] = "pk-test-xxx"
    os.environ["LANGFUSE_SECRET_KEY"] = "sk-test-xxx"
    # 之后所有 OpenAI 调用都会自动上报
    yield
    Langfuse().flush()
```

测试失败时，去 Langfuse 控制台看 trace：

```
[cs-001] Customer Service Agent
├── input: "我的订单 OD2026081901 到哪了？"
├── LLM Call 1 (gpt-4o-mini, 230ms, 120 tokens)
│   └── output: tool_call(search_order, id="OD2026081901")
├── Tool: search_order (50ms)
│   └── output: {"status": "已发货"}
└── LLM Call 2 (gpt-4o-mini, 180ms, 90 tokens)
    └── output: "您的订单 OD2026081901 已发货..."
```

### 18.11.2 自建"测试追踪"

如果不想接外部服务，最简的本地追踪：

```python
# 用 OpenTelemetry 自带 console exporter
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import ConsoleSpanExporter, SimpleSpanProcessor

provider = TracerProvider()
provider.add_span_processor(SimpleSpanProcessor(ConsoleSpanExporter()))
trace.set_tracer_provider(provider)
```

测试输出会打印每条 span 的耗时/属性。

---

## 18.12 成本与延迟门禁

**Agent 上线的隐性 KPI**：每次任务花多少钱、跑多久。

### 18.12.1 成本门禁

```python
# conftest.py
PRICING = {"gpt-4o": (5.0 / 1e6, 15.0 / 1e6),       # input $/token, output $/token
           "gpt-4o-mini": (0.15 / 1e6, 0.6 / 1e6)}

def calc_cost(model: str, input_tokens: int, output_tokens: int) -> float:
    pin, pout = PRICING[model]
    return input_tokens * pin + output_tokens * pout

@pytest.fixture
def cost_budget(monkeypatch):
    """任何 Agent 测试都自动检查成本不超 $0.05"""
    failures = []

    def check(response, threshold=0.05):
        cost = sum(calc_cost(c.model, c.input_tokens, c.output_tokens) for c in response.llm_calls)
        if cost > threshold:
            failures.append(f"成本 ${cost:.4f} 超阈值 ${threshold}")
        return cost

    monkeypatch.setattr("myagents.cs_agent.run", lambda *a, **kw: 
                        (check(_response), _response)[1])
    yield check
    if failures:
        pytest.fail("\n".join(failures))
```

### 18.12.2 延迟门禁

```python
import time

@pytest.mark.parametrize("case", GOLDEN_CASES, ids=[c["id"] for c in GOLDEN_CASES])
def test_latency(case):
    start = time.perf_counter()
    response = customer_service_agent.run(input=case["input"])
    elapsed = time.perf_counter() - start

    # p95 应 < 5s（mock LLM 时更快；真 LLM 时建议分桶记录）
    assert elapsed < 5.0, f"[{case['id']}] 延迟 {elapsed:.2f}s 超阈值"
```

### 18.12.3 复杂场景：用 Locust 压测 Agent

```python
# locustfile_agent.py —— 并发评测 Agent 吞吐
from locust import HttpUser, task

class AgentUser(HttpUser):
    @task
    def ask_cs(self):
        self.client.post("/chat", json={
            "input": "我的订单 OD2026081901 到哪了？"
        })

# 跑：locust -f locustfile_agent.py --users 10 --spawn-rate 2 --run-time 60s
```

观察：p50 / p95 / p99 延迟、QPS、成本/请求。

---

## 18.13 实战：为 "AutoGen 调研小组" 装 Harness

把第 17 章的 AutoGen 项目加上完整 Harness。

### 18.13.1 项目结构

```
market_research/
├── market_research/
│   ├── __init__.py
│   ├── team.py             # AutoGen GroupChat 组装
│   └── tools.py            # search_web 工具
├── tests/
│   ├── conftest.py
│   ├── unit/
│   │   └── test_tools.py
│   ├── integration/
│   │   └── test_team_vcr.py
│   └── e2e/
│       ├── test_team_real.py
│       └── golden.jsonl
├── pytest.ini
├── pyproject.toml
└── .github/workflows/ci.yml
```

### 18.13.2 `pytest.ini` 分层

```ini
[pytest]
testpaths = tests
markers =
    unit: 纯函数单元测试，快（< 1s）
    integration: 录制回放测试，中速（< 10s）
    e2e: 真实 LLM E2E，慢且贵
addopts = -ra --strict-markers --tb=short
```

### 18.13.3 单元测试：工具

```python
# tests/unit/test_tools.py
import pytest
from market_research.tools import search_web

@pytest.mark.unit
def test_search_web_returns_json():
    result = search_web("AutoGen")
    import json
    data = json.loads(result)
    assert "results" in data
    assert len(data["results"]) > 0
```

### 18.13.4 集成测试：录制回放

```python
# tests/integration/test_team_vcr.py
import pytest, vcr, json
from market_research.team import build_team

@pytest.mark.integration
@pytest.mark.vcr(cassette="tests/cassettes/cs_team.yaml", decode_compressed_response=True)
def test_team_runs_to_completion():
    team = build_team()
    result = team.run_sync(task="调研主题：2026 年 RAG 技术趋势")

    # 1. 终止条件触发
    assert any("APPROVED" in m.content for m in result.messages)

    # 2. 每个 Agent 都至少发言一次
    sources = {m.source for m in result.messages}
    assert {"planner", "researcher", "writer", "critic"} <= sources
```

### 18.13.5 E2E 测试：真 LLM + Golden Set + 成本门禁

```python
# tests/e2e/test_team_real.py
import json, pytest, time
from market_research.team import build_team

GOLDEN = [json.loads(l) for l in open("tests/e2e/golden.jsonl")]

@pytest.mark.e2e
@pytest.mark.parametrize("case", GOLDEN, ids=[c["id"] for c in GOLDEN])
def test_team_golden(case, cost_budget):
    team = build_team()
    start = time.perf_counter()
    result = team.run_sync(task=case["input"])
    elapsed = time.perf_counter() - start

    final = result.messages[-1].content
    for kw in case.get("must_contain", []):
        assert kw in final, f"[{case['id']}] 缺少: {kw}"

    assert elapsed < case.get("max_seconds", 60), f"超时 {elapsed:.1f}s"
    cost_budget(result)
```

### 18.13.6 GitHub Actions CI

```yaml
# .github/workflows/ci.yml
name: CI
on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: "3.11"
          cache: pip

      - run: pip install -e ".[dev]"

      # 1. 单元 + 集成（必须过）
      - name: Unit & Integration
        run: pytest -m "unit or integration" --cov=market_research --cov-fail-under=85

      # 2. E2E（只在 main + 定时跑，控制成本）
      - name: E2E (only main & nightly)
        if: github.ref == 'refs/heads/main' || github.event_name == 'schedule'
        env:
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
        run: pytest -m e2e
        continue-on-error: false
```

---

## 18.14 pytest 高级技巧

### 18.14.1 `xfail`：已知失败不阻塞 CI

```python
@pytest.mark.xfail(reason="LLM 偶尔幻觉，待优化 prompt", strict=False)
def test_no_hallucination_on_rare_topic():
    ...
```

### 18.14.2 `skip` / `skipif`

```python
@pytest.mark.skip(reason="等依赖升级")
def test_new_feature(): ...

@pytest.mark.skipif(not os.getenv("OPENAI_API_KEY"), reason="需 OpenAI key")
def test_with_llm(): ...
```

### 18.14.3 自定义 marker + 命令行筛选

```ini
# pytest.ini
markers =
    slow: 跑得很慢（默认跳过）
    llm: 需要 LLM key（默认跳过）
```

```bash
pytest -m "not slow and not llm"     # 默认：只跑快 + 不需 LLM
pytest -m "llm" --co                  # 只列出 llm 用例（--co = collect only）
pytest -m "llm and not slow"          # 跑 LLM 但跳过慢的
```

### 18.14.4 并行：`pytest-xdist`

```bash
pip install pytest-xdist
pytest -n auto    # 自动按 CPU 数并行
pytest -n 4       # 4 个 worker
```

> 注意：fixture 的 scope 设计要兼容并行（如 `scope="module"` 可能 race condition）。

### 18.14.5 用 `pytest --fixtures` 查可用夹具

```bash
pytest --fixtures
# 显示所有可用 fixture 的名字、作用域、来源
```

---

## 18.15 常见踩坑

| 坑 | 症状 | 解决 |
| --- | --- | --- |
| **fixture 没清理** | 测试间互相污染 | 用 `yield fixture` + 显式 teardown |
| **scope 选错** | 数据库被反复创建/销毁，慢 | 共享资源用 `scope="module"` / `"session"` |
| **覆盖率虚高** | `if x: pass` 也算 100% | 启用 `branch = true` 分支覆盖 |
| **变异测试永远 KILLED** | 写得太简单、断言太弱 | 加强断言到具体值 |
| **属性测试跑得慢** | Hypothesis 自动生成 1000 个用例 | 用 `settings(max_examples=50)` 控量 |
| **mock 残留** | 一次 mock 影响其他测试 | 用 `mocker.patch`（自动还原），不用 `monkeypatch.setattr` 改全局 |
| **Agent 测试慢** | 每次都调真 LLM | 用 `vcrpy` 录制回放 + `respx` mock |
| **LLM-as-Judge 不稳定** | 同样回答打分波动 | 多次打取平均 + 用更稳的 prompt + 控制温度 = 0 |
| **Golden Set 维护难** | 改 prompt 后一堆用例挂 | 分桶标记（`must_have` vs `nice_to_have`），不强制全过 |
| **成本爆掉** | E2E 跑一天花 $100 | 用更便宜模型 + 设硬阈值 + `--max-examples` 控制 |

---

## 18.16 面试高频问题

**Q1. 测试 Harness 和 CI 有什么区别？**

A：CI 是"什么时候跑"，Harness 是"用什么跑"。Harness 是工具集合，CI 是调度系统。GitHub Actions 是 CI，pytest + 覆盖率 + mutation + property 是 Harness。

**Q2. 为什么 100% 覆盖率还是出 bug？**

A：覆盖率只衡量"代码跑没跑过"，不衡量"断言是否正确"。变异测试解决后者——故意改坏代码，看测试能不能发现。

**Q3. AI Agent 测试和传统测试最大的区别？**

A：确定性 vs 概率性。传统测试断言"等于 3"，Agent 测试断言"达到合格标准"——必须用 LLM-as-Judge 或软断言。

**Q4. 如何让 Agent 测试又快又稳？**

A：录制回放（vcrpy）保存真实 LLM 响应；测试时回放，不调真 API。CI 默认跑 mock/录制回放版，每天定时跑一次真实 E2E。

**Q5. LLM-as-Judge 的偏差怎么应对？**

A：（1）用更强模型当裁判；（2）交换回答顺序消除位置偏差；（3）控制温度 = 0；（4）多次打分取平均；（5）加 few-shot 校准样本。

**Q6. 变异测试值得投入吗？**

A：核心业务逻辑（订单、计费、权限）值得。UI/胶水代码不值得。变异杀死率 ≥ 70% 是行业基线。

**Q7. 测试 Harness 和 AI Agent Harness 能共用吗？**

A：基础层（fixture、参数化、覆盖率、CI）完全共用。差异在断言层——Agent 多出工具调用断言、LLM-as-Judge、成本延迟断言、Golden Set 管理。

---

## 18.17 本章小结

| 你学到了 | 对应技能点 |
| --- | --- |
| Harness 概念与全景 | 测试 Harness + AI Agent Harness 双线 |
| pytest fixture 全套 | scope、嵌套、内置 fixture、conftest 层级 |
| 参数化 + Mock | 数据驱动 + pytest-mock + respx |
| 覆盖率 | pytest-cov + 行/分支覆盖 + 增量覆盖 |
| 变异测试 | mutmut + 变异杀死率 |
| 属性测试 | Hypothesis + 自定义策略 |
| Golden Set 管理 | JSONL 外置 + 分桶标记 |
| LLM-as-Judge | 工程化 + 偏差应对 |
| 录制回放 | vcrpy + respx 双方案 |
| 可观测 | Langfuse / OpenTelemetry |
| 成本延迟门禁 | token 计费 + p95 延迟 |
| AutoGen 项目 Harness 实战 | 单元/集成/E2E 三层 + GitHub Actions |

**收束**：Harness 不是"加测试"，而是"**给被测对象造一个**可观测、可重复、可控**的环境**"。对 Agent 而言，这个环境里还多了 LLM 概率性、工具调用、token 成本——Harness 就是把这些"软变量"转成"硬指标"的工程化手段。

**下一章预告**：第 19 章把视角从"测试"扩展到"**评测驱动的开发**"（Eval-Driven Development）——把 Harness 跑分作为 Agent 迭代的核心反馈循环，用数据驱动 Prompt / 模型 / 工具的演化。