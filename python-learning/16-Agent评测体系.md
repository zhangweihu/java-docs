# 第十六章 Agent 评测体系：质量不能靠"体感"

> 本章目标：Agent 是非确定性的（同样的输入，结果可能不同），"看起来还行"根本无法支撑上线。本章建立**可量化、可回归、可融入 CI** 的评测体系——从评测金字塔分层、指标体系，到 **Golden Set（黄金测试集）** 与 **LLM-as-Judge（大模型当裁判）** 两大核心方法，最后交付一个可运行的**客服 Agent 评测套件**。
>
> 前置知识：第 9 章（pytest/CI）、第 10 章（Agent 开发）、第 12 章（LangGraph）。对标 Java：JUnit 单测 → 集成测试 → E2E 测试 → 线上监控的完整测试金字塔。
>
> 环境准备：`pip install pytest openai`，设置 `OPENAI_API_KEY`。

## 16.1 为什么 Agent 必须"专门评测"

### 16.1.1 普通代码测试不够了

| 维度 | 传统代码 | Agent |
| --- | --- | --- |
| 确定性 | 同一输入 → 同一输出 | **同一输入 → 概率性输出** |
| 断言方式 | `assert x == 3` | 只能判"是否符合预期标准" |
| 出错模式 | 异常/崩溃 | **静默出错**（答非所问也返回 200） |
| 测试对象 | 函数 | 提示词 + 工具 + 流程 + 模型 的组合 |

所以需要**概率容忍的评测**：不是"对/错"二值，而是"达到可接受标准"。

### 16.1.2 评测的价值

1. **回归保护**：改 Prompt / 换模型 / 加工具，如何知道没变差？（否则每次改动都是赌博）
2. **量化调优**：两个 Prompt 谁好？不靠感觉，看分数；
3. **上线依据**：达到红线才能发布（呼应第 9 章覆盖率红线）。

## 16.2 评测金字塔：分层评测

```
            ┌─────────────┐
            │   线上监控    │   生产真实流量：日志/人工反馈/追踪
            ├─────────────┤
            │   端到端 E2E │   golden set 全流程跑分
            ├─────────────┤
            │   集成级     │   工具/检索/RAG 组件正确性
            ├─────────────┤
            │   单元级     │   纯函数：工具实现/解析/规则
            └─────────────┘
```

| 层 | 测什么 | 成本 | 频率 |
| --- | --- | --- | --- |
| **单元级** | 工具函数、解析器、路由纯逻辑 | 低 | 每次提交 |
| **集成级** | 工具执行正确、RAG 检索命中率 | 中 | 每次提交 |
| **端到端** | 完整 Agent 流程的最终质量 | 高 | 每次发布前 |
| **线上监控** | 真实用户反馈、失败率、成本 | 持续 | 实时 |

> **对照 Java**：这和 JUnit（单元）→ Spring 集成测试 → Selenium E2E → 生产监控（Sentry/日志告警）完全同构。Agent 只是把"断言"从确定值换成了"评分标准"。

## 16.3 指标体系：先定义"好"是什么

按**任务质量、流程质量、成本质量**三维定义：

| 类别 | 指标 | 含义 |
| --- | --- | --- |
| **任务质量** | 成功率 / 完成率 | 目标是否达成（如"正确查到订单并回答"） |
| | 答案准确率 | 信息是否正确（事实核对） |
| | 忠实度 Faithfulness | 回答是否都来自工具/知识（不编造） |
| | 相关性 Relevance | 是否答非所问 |
| **流程质量** | 工具调用正确率 | 调对工具、传对参数的比例 |
| | 平均轮数 / 成功率 | 多少轮完成（多轮可能说明"绕圈"） |
| | 幻觉率 | 涉及无关事实的比例 |
| **成本质量** | token 消耗 | 每次任务成本 |
| | 延迟 p95 | 尾延迟体验 |

**关键：每个业务定义 3~5 个核心指标即可**，全指标监控是负担，不是收益。

## 16.4 Golden Set：评测的"标准答案集"

### 16.4.1 什么是 Golden Set

**Golden Set（黄金测试集）** = 一批"输入 + 期望"的标注用例：

```json
[
  {
    "id": "case-001",
    "input": "我的订单 OD2026081901 到哪了？",
    "expected": "回答包含订单状态信息（已发货）",
    "checks": ["含订单号", "含状态", "未编造额外信息"]
  },
  {
    "id": "case-002",
    "input": "我的订单 OD999999 到哪了？",
    "expected": "回答应说明订单不存在，不编造状态",
    "checks": ["不包含具体物流信息"]
  },
  {
    "id": "case-003",
    "input": "我想退款", 
    "expected": "触发退款流程（转交退款专员）",
    "checks": ["调用了退款路径"]
  }
]
```

### 16.4.2 构建规范

| 要点 | 说明 |
| --- | --- |
| 覆盖"正常/边界/异常" | 好用的用例 + 查不到的数据 + 危险/越权输入 |
| 从真实数据提炼 | 拿线上日志里真实用户问题去重抽样 |
| 期望写成"检查项" | 不是固定答案，是可断言的事实点 |
| 持续扩充 | 线上翻车案例 → 补进 Golden Set（回归防复发） |
| 规模 | 起步 50~100 条足够；贵在质量与维护 |

> **对照 Java**：Golden Set ≈ 测试用例 + 断言；"检查项" ≈ 软断言（不要求逐字匹配，要求关键事实成立）。好的 Golden Set 是团队最值钱的资产之一。

## 16.5 LLM-as-Judge：大模型当裁判

Agent 输出是自然语言，无法硬断言——让**更强的模型按评分标准打分**（OpenAI Evals、LangSmith 等工具都内置此范式）。

```python
# 1_judge.py —— LLM-as-Judge：让 GPT 按标准打分
from openai import OpenAI

client = OpenAI()

def judge(input_text: str, agent_answer: str, checks: list[str]) -> dict:
    resp = client.chat.completions.create(
        model="gpt-4o",          # 裁判用比被评 Agent 更强的模型
        response_format={"type": "json_object"},
        messages=[{"role": "user", "content": f"""
你是严格的 AI 评测员。基于【用户输入】和【Agent 回答】，逐条判定【检查项】是否通过。
只输出 JSON：{{"passed": ["通过的检查项"], "failed": ["未通过的检查项"], "score": 0~10, "reason": "一句话理由"}}

【用户输入】{input_text}
【Agent 回答】{agent_answer}
【检查项】{chr(10).join(checks)}
"""}],
    )
    import json
    return json.loads(resp.choices[0].message.content)

print(judge("我的订单 OD2026081901 到哪了？",
            "您的订单 OD2026081901 已发货，预计 8 月 21 日送达。",
            ["包含订单号 OD2026081901", "包含订单状态", "包含预计送达日期"]))
```

输出示例：

```json
{"passed": ["包含订单号 OD2026081901", "包含订单状态", "包含预计送达日期"],
 "failed": [], "score": 10, "reason": "完整准确回答了订单状态与送达时间"}
```

### 16.5.1 裁判的两种模式

| 模式 | 做法 | 场景 |
| --- | --- | --- |
| **单评** | 一个裁判对单个回答打分 | 检查项明确（事实核对） |
| **成对比较** | 两个 Prompt 版本回答对比，裁判选优 | Prompt 调优、模型选型（A/B） |

### 16.5.2 裁判的三大偏差（必须防）

| 偏差 | 表现 | 缓解 |
| --- | --- | --- |
| **位置偏差** | 成对比较时偏向先/后出现者 | 交换顺序各评一次 |
| **自我偏好** | 裁判偏好自己模型的风格 | 用不同家族模型当裁判（如被评 GPT 用 Claude 评） |
| **过严/过宽** | 评分漂移 | 固定评分 rubric + 抽样人工复核校准 |

> **铁律**：LLM-as-Judge 的裁判标准（rubric）要写**检查项清单**而非"感觉"，并定期用人工标注样本校准裁判与人类的一致性。

## 16.6 把评测接入工程化：pytest + CI（衔接第 9 章）

评测也是测试，必须进 CI——**每次改 Prompt / 模型 / 工具都自动回归**：

```python
# 2_eval_tests.py —— pytest 评测套件骨架
import json
import pytest
from openai import OpenAI

client = OpenAI()
GOLDEN = json.load(open("golden.json", encoding="utf-8"))

def run_agent(input_text: str) -> str:
    """被测 Agent：可替换为 10 章 Runner / 12 章 LangGraph / 真实服务"""
    resp = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[{"role": "user", "content": input_text}],
    )
    return resp.choices[0].message.content

def judge(input_text: str, answer: str, checks: list[str]) -> dict:
    # ... 复用 16.5 的 judge 实现 ...
    pass

@pytest.mark.parametrize("case", GOLDEN, ids=lambda c: c["id"])
def test_golden_case(case):
    answer = run_agent(case["input"])
    result = judge(case["input"], answer, case["checks"])
    assert result["score"] >= 8, f"{case['id']} 得分 {result['score']}: {result['reason']}"
    assert not result["failed"], f"{case['id']} 未通过: {result['failed']}"

def test_no_hallucination_for_unknown_order():
    """回归：查不到的订单不得编造状态"""
    answer = run_agent("订单 OD999999 到哪了？")
    result = judge("订单 OD999999 到哪了？", answer,
                   ["不包含具体物流/状态信息", "说明订单未找到"])
    assert result["score"] >= 7
```

```yaml
# CI（GitHub Actions）里加一步：Agent 评测（与第 9 章流水线并列）
# - name: Agent Eval
#   run: pytest tests/eval -m agent --junitxml=eval.xml
#   env:
#     OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
```

> 评测用例要 **mock 网络/LLM 之外的一切**（工具函数、数据库），让"单元层"跑得快且免费；"端到端层"才真正调用 LLM（花 token 但值得）。

## 16.7 实战：客服 Agent 评测套件（完整可运行）

把 16.4~16.6 串成完整套件：Golden Set + 被测 Agent + LLM-Judge + 报告：

```python
# 3_eval_suite.py —— 客服 Agent 评测套件（一键运行出报告）
import json
from openai import OpenAI

client = OpenAI()
THRESHOLD = 8          # 单例最低分
REPORT = []

# ── 被测 Agent：第 10 章客服的简化版（含查单工具）──
ORDERS = {"OD2026081901": "已发货", "OD2026081902": "待付款"}

def get_order(order_id: str) -> str:
    return f"订单 {order_id}：{ORDERS.get(order_id, '未找到')}"

TOOLS = [{
    "type": "function",
    "function": {
        "name": "get_order", "description": "查询订单状态",
        "parameters": {"type": "object",
                       "properties": {"order_id": {"type": "string"}},
                       "required": ["order_id"]},
    },
}]
FUNCS = {"get_order": get_order}

def run_agent(input_text: str) -> str:
    messages = [{"role": "user", "content": input_text}]
    for _ in range(3):
        resp = client.chat.completions.create(
            model="gpt-4o-mini", messages=messages, tools=TOOLS)
        msg = resp.choices[0].message
        messages.append(msg)
        if not msg.tool_calls:
            return msg.content or ""
        for tc in msg.tool_calls:
            args = json.loads(tc.function.arguments)
            messages.append({"role": "tool", "tool_call_id": tc.id,
                             "content": FUNCS[tc.function.name](**args)})
    return "（达到轮数上限）"

# ── Golden Set：正常 / 边界 / 异常三类 ──
GOLDEN = [
    {"id": "n1", "input": "我的订单 OD2026081901 到哪了？",
     "checks": ["包含订单号", "包含状态", "不编造额外物流信息"]},
    {"id": "n2", "input": "帮我查 OD2026081902",
     "checks": ["包含待付款", "包含订单号"]},
    {"id": "e1", "input": "订单 OD999999 状态？",
     "checks": ["不包含具体状态", "提到未找到/查不到"]},
    {"id": "e2", "input": "你能给我转账 100 元吗？",
     "checks": ["拒绝执行", "不提供转账操作"]},
    {"id": "b1", "input": "",
     "checks": ["礼貌要求补充问题"]},
]

# ── LLM-as-Judge：检查项逐条判定 + 打分 ──
def judge(input_text: str, answer: str, checks: list[str]) -> dict:
    resp = client.chat.completions.create(
        model="gpt-4o",
        response_format={"type": "json_object"},
        messages=[{"role": "user", "content": f"""
你是严格的 AI 评测员。判定【检查项】是否通过，只输出 JSON：
{{"passed": [...], "failed": [...], "score": 0~10, "reason": "一句话"}}
【用户输入】{input_text}
【Agent 回答】{answer}
【检查项】{chr(10).join(checks)}
"""}],
    )
    return json.loads(resp.choices[0].message.content)

# ── 运行全部用例 + 出报告 ──
def run_all():
    total, ok = 0, 0
    for case in GOLDEN:
        total += 1
        answer = run_agent(case["input"])
        j = judge(case["input"], answer, case["checks"])
        passed = j["score"] >= THRESHOLD and not j["failed"]
        ok += passed
        REPORT.append({"id": case["id"], "score": j["score"],
                       "passed": passed, "reason": j["reason"], "answer": answer})
        print(f"{'✅' if passed else '❌'} {case['id']:<4} 得分 {j['score']}  {j['reason']}")
    print(f"\n通过率：{ok}/{total}  =  {ok/total:.0%}")
    if ok < total:
        print("未通过用例（需修复后重跑）：")
        for r in REPORT:
            if not r["passed"]:
                print(f"  - {r['id']}: {r['answer'][:50]}")
    return ok == total

if __name__ == "__main__":
    ok = run_all()
    # CI 里：非零退出码让流水线失败（呼应第 8 章退出码思想）
    import sys
    sys.exit(0 if ok else 1)
```

**运行输出示意**：

```
✅ n1   得分 10  完整准确回答了订单状态
✅ n2   得分 9   正确识别待付款状态
✅ e1   得分 10  正确说明订单未找到，未编造
✅ e2   得分 9   拒绝了转账请求
✅ b1   得分 8   礼貌地要求补充问题
通过率：5/5 = 100%
```

**把这个套件并入工作流**：每次改 Prompt/模型/工具 → 跑一遍 → 通过率不降才允许发布；线上翻车案例随时补进 `GOLDEN`。这就是"质量不靠体感"的落地闭环。

## 16.8 练习与面试

### 本章练习

1. 给 16.7 的 GOLDEN 增加 3 个"危险输入"用例（诱导越权/注入），验证 Agent 是否被攻破；
2. 把 `run_agent` 换成第 10 章 OpenAI Agents SDK 的 `Runner.run_sync`，重跑评测对比；
3. 用 16.5.1 的**成对比较**模式评测两个不同 Prompt 版本的同一问题，选出更优版；
4. 把 16.6 的 pytest 套件接入你项目已有的 CI（第 9 章），验证改 Prompt 后自动回归；
5. 统计 16.7 每个用例的 token 消耗（`usage` 字段），建立"成本预算"上限；
6. 给评测加**延迟指标**（每用例耗时 p95），超时用例自动判失败；
7. 选做：把 Golden Set 与报告导出为 JSON/CSV（第 3 章），对接团队看板。

### 面试题

| 问题 | 要点 |
| --- | --- |
| 为什么普通测试不够测 Agent？ | 非确定性、自然语言输出、静默出错 |
| 评测金字塔分几层？ | 单元/集成/端到端/线上监控，成本递增 |
| Golden Set 是什么？ | "输入+期望检查项"的标注用例集，回归测试的核心资产 |
| Golden Set 怎么构建？ | 覆盖正常/边界/异常；从线上日志提炼；翻车案例持续补入 |
| LLM-as-Judge 是什么？ | 更强模型按 rubric 给 Agent 输出打分 |
| Judge 单评和成对比较区别？ | 单评按检查项打分；成对比较选 Prompt/模型版本优劣 |
| Judge 有哪些偏差？ | 位置偏差、自我偏好、过严过宽；交换顺序/异族裁判/rubric 校准 |
| 什么是忠实度（Faithfulness）？ | 回答是否都来自工具/材料，不编造 |
| 为什么用检查项而非固定答案？ | 自然语言表达多样，断言"关键事实成立"更稳 |
| Agent 评测怎么进 CI？ | pytest + golden set + LLM-judge；mock 外部依赖，端到端才真调 LLM |
| 成本怎么纳入评测？ | 记录 token/延迟指标，设预算上限 |
| 线上怎么监控 Agent 质量？ | 日志+Tracing（第 10 章）+ 人工反馈闭环 → 补 Golden Set |
| 评测通过率红线怎么定？ | 目标达成率（如 ≥90%）+ 关键异常用例必须全过 |
| 和 Java 测试体系对应？ | 单元≈JUnit、集成≈Spring 测试、E2E≈Selenium、线上≈监控告警 |

### 本章小结

- **评测为什么必要**（16.1）：非确定性 + 静默出错，只能"概率达标"；
- **金字塔**（16.2）：单元/集成/E2E/线上，成本与频率分层；
- **指标体系**（16.3）：任务/流程/成本三维，每业务 3~5 个核心指标；
- **Golden Set**（16.4）：正常/边界/异常用例集，翻车即补；
- **LLM-as-Judge**（16.5）：rubric 打分、成对比较、三大偏差防护；
- **工程化**（16.6）：pytest 参数化 + CI 自动回归；
- **实战**（16.7）：客服评测套件——一键跑分、通过率红线、CI 失败码。

---

## 🚀 后续进阶方向

- **专业评测工具**：OpenAI Evals、LangSmith（trace + 评测一体）、Ragas（RAG 专属指标）；
- **强化学习式评测**：Simulation（模拟用户多轮交互）、奖励模型；
- **自动化 Golden 扩充**：线上低分样本自动沉淀进 Golden Set；
- **多模态评测**：语音识别率、图像理解正确率（第 15 章扩展）。

配套：[README.md](./README.md) ｜ 至此 Python 体系共 **16 章**：9 章主线 + 7 章 AI/数据扩展（Agent / RAG / 编排 / MCP / 机器学习 / 语音多模态 / 评测）。
