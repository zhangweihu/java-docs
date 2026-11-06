# 第二十三章 推理框架与 Ollama 本地部署：把大模型跑在自己的机器上

> 本章目标：第 22 章理解了 MoE 等模型架构，本章解决"**怎么把它跑起来**"。从推理核心技术（KV Cache / PagedAttention / 连续批处理 / 量化）讲起，横向对比 **vLLM / SGLang / TGI / TensorRT-LLM / llama.cpp / Ollama** 六大推理框架，重点实战 **Ollama**，最后给出**本地部署大模型的完整决策流程**：显存估算 → 硬件选型 → 三条部署路线 → OpenAI 兼容接入 → 与 RAG/Agent 集成 → 压测监控 → 私有化 RAG 服务实战。
>
> 前置知识：第 6 章（FastAPI）、第 10 章（Agent）、第 11 章（RAG）、第 19 章（LLM 基础）、第 22 章（MoE）。对标 Java：推理框架 ≈ Tomcat/Netty 之于 Web 应用——模型是"业务代码"，推理框架是"决定吞吐与延迟的应用服务器"。
>
> 环境准备：`pip install openai requests chromadb fastapi uvicorn pydantic`，并安装 Ollama（https://ollama.com/download）。示例兼顾 Windows / macOS / Linux。

---

## 23.1 为什么需要专门的推理框架

### 23.1.1 裸 `transformers.generate()` 的问题

第 19 章用 `pipeline` / `model.generate()` 能跑，但生产环境会立刻撞墙：

```python
# ❌ 教学可用，生产不行
from transformers import AutoModelForCausalLM, AutoTokenizer

model = AutoModelForCausalLM.from_pretrained("Qwen/Qwen2.5-7B-Instruct").cuda()
out = model.generate(**inputs, max_new_tokens=512)
```

| 问题 | 后果 |
| --- | --- |
| **逐请求串行** | 第二个用户必须等第一个生成完，GPU 利用率极低 |
| **显存预留固定** | 按最大长度预留 KV Cache，短请求也占满 → 显存浪费 60%+ |
| **无 KV Cache 复用** | 多轮对话重复计算历史 Token |
| **无量化/并行** | 大模型单卡装不下 |
| **无指标/限流** | 无法监控 TTFT、吞吐、OOM 无保护 |

### 23.1.2 推理 ≠ 训练

| 维度 | 训练 | 推理 |
| --- | --- | --- |
| 计算模式 | 大 batch 矩阵乘（计算密集） | **逐 Token 自回归**（访存密集） |
| 瓶颈 | FLOPs | **显存带宽**（每生成一个 Token 都要读全部权重） |
| 优化目标 | 收敛速度 | **TTFT / TPOT / 吞吐 / 并发** |
| 精度 | FP16/BF16 + 梯度 | 可量化到 INT8/INT4 |

> **关键**：推理是 **memory-bound**（内存带宽受限）。所以量化和 KV Cache 优化（减少要读的字节数）比堆算力更管用。

### 23.1.3 推理框架 = 大模型的"应用服务器"

```
┌──────────────────────────────────────────────┐
│  你的应用（Agent / RAG / Web / 第10~21章）    │
└───────────────────┬──────────────────────────┘
                    │ OpenAI 兼容 API  ← 统一契约
┌───────────────────▼──────────────────────────┐
│  推理框架（vLLM / SGLang / Ollama / ...）    │
│  · 批处理调度   · KV Cache 管理              │
│  · 量化 / 并行   · 流式输出   · 指标监控      │
└───────────────────┬──────────────────────────┘
                    │
┌───────────────────▼──────────────────────────┐
│  模型权重（HF / GGUF / AWQ / GPTQ）          │
└──────────────────────────────────────────────┘
```

> **本章最重要的实践结论**：**所有主流推理框架都提供 OpenAI 兼容 API**。这意味着前面 10~21 章的代码，只要改一行 `base_url` 就能切换成本地模型，几乎零改造。

---

## 23.2 推理核心技术（先懂原理再选框架）

### 23.2.1 自回归生成与 Prefill / Decode

```
Prefill（预填充）: 一次性处理整个 Prompt，算出 KV Cache     ← 计算密集
Decode（解码）  : 一个一个吐 Token，每次复用 KV Cache      ← 访存密集
```

| 指标 | 全称 | 含义 | 体验对应 |
| --- | --- | --- | --- |
| **TTFT** | Time To First Token | 首 Token 延迟 | "等多久开始出字" |
| **TPOT** | Time Per Output Token | 每 Token 间隔 | "打字快不快" |
| **Throughput** | tokens/s | 总吞吐 | 服务端能扛多少并发 |
| **E2E Latency** | 总耗时 | TTFT + TPOT × N | 整体响应 |

### 23.2.2 KV Cache

自回归生成第 \(n\) 个 Token 时，前面所有 Token 的 Key/Value 都要参与 Attention。缓存它们就避免重复计算：

```python
# 伪代码
past_kv = None
for step in range(max_new_tokens):
    logits, past_kv = model(next_token, past_key_values=past_kv)  # 复用缓存
    next_token = sample(logits)
```

**代价**：KV Cache 随「batch × 序列长度 × 层数 × head 数」线性增长，长上下文时**比模型权重还大**：

\[
\text{KV Cache} = 2 \times L \times H_{kv} \times d_h \times S \times B \times \text{bytes}
\]

> **例**：7B 模型 32 层、32 heads、head_dim=128、FP16、seq=4096、batch=1
> ≈ 2×32×32×128×4096×1×2B ≈ **2 GB**（GQA 后可降到 0.25GB）

### 23.2.3 PagedAttention（vLLM 的核心创新）

借鉴操作系统**虚拟内存分页**：把 KV Cache 切成固定大小的"块（block）"，按需分配，不连续存储。

```
传统：为每个请求预留连续的 max_len 空间 ──► 大量内部碎片（浪费 60~80%）
分页：用块表(block table)映射逻辑块到物理块 ──► 几乎零浪费
```

**收益**：同样的显存能塞下 **2~4 倍并发**；还支持 **前缀共享**（相同 system prompt 的多个请求共享 KV 块）。

### 23.2.4 Continuous Batching（连续批处理）

```
静态批处理（老）：[A,B,C] 一起进，必须等最慢的 C 完成才放下一批  ← GPU 空转
连续批处理（新）：C 还在生成时，A 完成了就立刻插入新请求 D      ← GPU 不空转
```

这是 vLLM / SGLang / TGI 吞吐远高于裸 `generate()` 的首要原因。

### 23.2.5 量化：显存与速度的杠杆

| 格式 | 每参数字节 | 7B 模型权重 | 特点 |
| --- | --- | --- | --- |
| FP32 | 4 | 28 GB | 训练用，推理不用 |
| FP16 / BF16 | 2 | 14 GB | 无损基线 |
| INT8 | 1 | 7 GB | 基本无损 |
| **INT4 / Q4_K_M** | 0.5 | **3.5 GB** | 主流本地选择，轻微损失 |
| Q2 / Q3 | ~0.3 | ~2 GB | 明显降智，仅极限场景 |

**常见量化格式对照**：

| 格式 | 生态 | 适用 |
| --- | --- | --- |
| **GGUF** | llama.cpp / Ollama / LM Studio | CPU / Mac / 消费级 GPU，**本地部署首选** |
| **AWQ / GPTQ** | vLLM / TGI / TensorRT-LLM | GPU 服务端，吞吐优先 |
| **FP8** | H100 / vLLM / TRT-LLM | 新卡，精度与速度平衡 |
| **BNB (bitsandbytes)** | HF transformers | 微调 / 快速实验 |

### 23.2.6 其他加速技术

| 技术 | 原理 | 收益 |
| --- | --- | --- |
| **FlashAttention** | IO 感知，避免物化 N×N 注意力矩阵 | 长上下文提速省显存 |
| **GQA / MLA** | 多个 Query 共享少量 KV head | KV Cache 大幅缩小 |
| **Speculative Decoding** | 小模型草拟 N 个 Token，大模型一次性验证 | 解码提速 2~3×（不改输出分布） |
| **Chunked Prefill** | 把长 prefill 切块与 decode 交错 | 降低 TTFT 抖动 |
| **Tensor Parallel (TP)** | 权重切到多卡，每卡算一部分 | 单请求也能用多卡 |
| **Expert Parallel (EP)** | MoE 专家分布到多卡（呼应第 22 章） | MoE 必备 |
| **Prefix Caching** | 复用相同前缀的 KV | 长 system prompt / RAG 场景省 50%+ |

---

## 23.3 推理框架全景与选型

### 23.3.1 六大框架对比

| 框架 | 定位 | 核心优势 | 硬件 | 适合场景 |
| --- | --- | --- | --- | --- |
| **vLLM** | 云端生产事实标准 | PagedAttention、连续批处理、吞吐最高、生态最广 | NVIDIA GPU（也支持 AMD/CPU 实验） | **高并发在线服务**首选 |
| **SGLang** | 新一代高性能 | RadixAttention 前缀缓存、结构化输出/Agent 场景强 | NVIDIA GPU | 多轮 Agent、复杂提示、高吞吐 |
| **TGI** | HuggingFace 官方 | 与 HF 生态无缝、部署简单 | NVIDIA GPU | 已在用 HF 生态的团队 |
| **TensorRT-LLM** | NVIDIA 极致优化 | 单卡延迟最低、FP8/量化最全 | **仅 NVIDIA**，需编译引擎 | 追求极限性能、有编译耐心 |
| **llama.cpp** | 轻量 CPU 推理 | GGUF、无依赖、CPU/Metal/显卡都能跑 | CPU / Apple Silicon / 任意 | **无 GPU / 边缘 / Mac** |
| **Ollama** | 本地开发体验之王 | 一条命令跑模型、自动量化/驱动、OpenAI 兼容 | CPU / Apple / NVIDIA / AMD | **个人开发、原型、私有化小服务** |
| **LM Studio** | 图形界面 | 零命令行、可视化选模型 | 同上 | 完全不想敲命令的用户 |
| **MLX / MLX-LM** | Apple 专属 | 统一内存优化 | Apple Silicon | Mac 本地部署 |

> **关系澄清**：**Ollama 底层就是 llama.cpp**（封装了模型管理、服务、API）。LM Studio 也基于 llama.cpp。它们不是竞争关系，是"同一引擎的不同外壳"。

### 23.3.2 选型决策树

```
你要在什么环境跑？
│
├─ 有 NVIDIA 数据中心卡 / 要对外提供高并发服务？
│   ├─ 追求生态稳、文档多 ──────────► vLLM ⭐
│   ├─ Agent/多轮/结构化输出为主 ───► SGLang
│   └─ 追求极限延迟且有编译能力 ────► TensorRT-LLM
│
├─ 只有消费级显卡 / 笔记本 / 个人开发？
│   ├─ 想最快跑起来 ────────────────► Ollama ⭐
│   ├─ 完全不想用命令行 ────────────► LM Studio
│   └─ 要自己嵌进 C++/Python 程序 ──► llama.cpp
│
├─ 只有 CPU / 无独显？
│   └─ llama.cpp (GGUF Q4) 或 Ollama   ← 能跑，但慢（几 tokens/s）
│
└─ Apple Silicon (M 系列)？
    ├─ 通用 ─────────────────────────► Ollama / LM Studio（Metal 加速）
    └─ 追求性能 ────────────────────► MLX-LM（统一内存，可跑大模型）
```

### 23.3.3 快速上手 vLLM（对照了解）

```bash
# 安装（需 NVIDIA GPU + CUDA）
pip install vllm

# 启动 OpenAI 兼容服务
vllm serve Qwen/Qwen2.5-7B-Instruct \
  --port 8000 \
  --gpu-memory-utilization 0.9 \
  --max-model-len 8192 \
  --dtype auto
```

```python
# 客户端：和调 OpenAI 一模一样
from openai import OpenAI

client = OpenAI(base_url="http://localhost:8000/v1", api_key="unused")
r = client.chat.completions.create(
    model="Qwen/Qwen2.5-7B-Instruct",
    messages=[{"role": "user", "content": "用一句话解释 MoE"}],
)
print(r.choices[0].message.content)
```

> 后面 Ollama 的接入代码与这段**几乎完全相同**——这就是 OpenAI 兼容 API 的价值。

---

## 23.4 Ollama 实战

### 23.4.1 安装

**Windows / macOS**：到 https://ollama.com/download 下载安装包，双击安装。安装后托盘会有图标，默认服务运行在 `http://localhost:11434`。

**Linux**：

```bash
curl -fsSL https://ollama.com/install.sh | sh
```

**Docker**（适合服务器）：

```bash
# CPU
docker run -d -v ollama:/root/.ollama -p 11434:11434 --name ollama ollama/ollama

# NVIDIA GPU（需 nvidia-container-toolkit）
docker run -d --gpus=all -v ollama:/root/.ollama -p 11434:11434 --name ollama ollama/ollama
```

**验证**：

```bash
ollama --version
curl http://localhost:11434/api/version
```

### 23.4.2 常用命令速查

| 命令 | 作用 |
| --- | --- |
| `ollama pull qwen2.5:7b` | 下载模型 |
| `ollama run qwen2.5:7b` | 交互式对话（不存在会自动 pull） |
| `ollama list` | 已安装模型 |
| `ollama ps` | 正在运行的模型、显存占用 |
| `ollama show qwen2.5:7b` | 模型信息（参数量/量化/上下文/模板） |
| `ollama rm qwen2.5:7b` | 删除模型 |
| `ollama cp qwen2.5:7b my-bot` | 复制改名 |
| `ollama create my-bot -f Modelfile` | 用 Modelfile 定制模型 |
| `ollama serve` | 手动启动服务（通常自动运行） |
| `ollama stop qwen2.5:7b` | 卸载模型释放显存 |

### 23.4.3 模型命名与量化选择

Ollama 模型名格式：`模型:尺寸-量化级别`

```bash
ollama pull qwen2.5:7b            # 默认 Q4_K_M，约 4.7GB
ollama pull qwen2.5:7b-q4_0       # 更小更快，质量略降
ollama pull qwen2.5:7b-q8_0       # 更准，约 8GB
ollama pull qwen2.5:7b-fp16       # 无损，约 15GB
ollama pull llama3.1:8b
ollama pull deepseek-r1:7b        # 推理模型
ollama pull qwen2.5-coder:7b      # 代码专用
ollama pull nomic-embed-text      # 嵌入模型（RAG 用，重要！）
```

**常用模型推荐**（2026 年视角，按用途）：

| 用途 | 推荐 | 说明 |
| --- | --- | --- |
| 中文通用对话 | `qwen2.5:7b` / `qwen3:8b` | 中文最强梯队 |
| 代码 | `qwen2.5-coder:7b` | 代码补全/审查 |
| 推理/数学 | `deepseek-r1:7b` / `qwen3:8b` | 带思维链，慢但准 |
| 嵌入（RAG） | `nomic-embed-text` / `bge-m3` | **不能用聊天模型做 embedding** |
| 多模态 | `llava` / `qwen2.5vl` | 图像理解 |
| 极小设备 | `qwen2.5:0.5b` / `1.5b` | 几 GB 内存也能跑 |

### 23.4.4 Windows 常用环境变量

在 PowerShell 里设置（或"系统属性 → 环境变量"永久设置）：

```powershell
# 模型存放位置（默认在 C:\Users\<你>\.ollama\models，容易撑爆 C 盘）
$env:OLLAMA_MODELS = "D:\ollama\models"

# 监听地址（默认只本机；局域网要访问设 0.0.0.0）
$env:OLLAMA_HOST = "0.0.0.0:11434"

# 模型常驻显存时间（默认 5m；设 -1 常驻，避免反复加载）
$env:OLLAMA_KEEP_ALIVE = "30m"

# 单请求并发数、加载 GPU 层数
$env:OLLAMA_NUM_PARALLEL = "2"
$env:OLLAMA_GPU_LAYERS = "999"

# 重启服务生效
ollama serve
```

> **踩坑提醒**：改 `OLLAMA_MODELS` 后已下载的模型**不会自动迁移**，需要手动移动或重新 pull。

### 23.4.5 Modelfile：定制自己的模型

```dockerfile
# Modelfile —— 定制一个"Python 代码审查助手"
FROM qwen2.5-coder:7b

PARAMETER temperature 0.1
PARAMETER num_ctx 8192
PARAMETER top_p 0.9

SYSTEM """
你是一名资深 Python 代码审查专家。
只输出三类问题：安全 / 性能 / 可维护性。
每条格式：`[级别] 行号: 问题 + 修改建议`。
不解释常识，不写代码全文。
"""

MESSAGE user "审查：def f(x): return eval(x)"
MESSAGE assistant "[BLOCKER] 1: eval 任意代码执行，改用 ast.literal_eval 或显式解析"
```

```bash
ollama create py-reviewer -f Modelfile
ollama run py-reviewer
```

**Modelfile 关键指令**：

| 指令 | 作用 |
| --- | --- |
| `FROM` | 基础模型（必填） |
| `PARAMETER` | 生成参数（`temperature` / `num_ctx` / `top_p` / `repeat_penalty`） |
| `SYSTEM` | 系统提示（人设） |
| `TEMPLATE` | 提示模板（高级，一般别改） |
| `MESSAGE` | few-shot 示例 |
| `ADAPTER` | 挂 LoRA 适配器 |

### 23.4.6 REST API

**① 原生 `/api/generate`**

```python
# 1_ollama_native.py
import requests, json

resp = requests.post(
    "http://localhost:11434/api/generate",
    json={
        "model": "qwen2.5:7b",
        "prompt": "用一句话解释 KV Cache",
        "stream": False,
        "options": {"temperature": 0.2, "num_ctx": 4096},
    },
    timeout=120,
)
print(resp.json()["response"])
```

**② 流式输出**

```python
# 2_ollama_stream.py
import requests, json

with requests.post(
    "http://localhost:11434/api/generate",
    json={"model": "qwen2.5:7b", "prompt": "写一首关于 Python 的短诗", "stream": True},
    stream=True, timeout=120,
) as r:
    r.raise_for_status()
    for line in r.iter_lines():
        if not line:
            continue
        chunk = json.loads(line)
        print(chunk.get("response", ""), end="", flush=True)
        if chunk.get("done"):
            print(f"\n\n[耗时 {chunk['total_duration']/1e9:.1f}s, "
                  f"生成 {chunk['eval_count']} tokens]")
            break
```

**③ 聊天 / 嵌入 / 模型管理**

```python
import requests
BASE = "http://localhost:11434"

# 多轮对话
requests.post(f"{BASE}/api/chat", json={
    "model": "qwen2.5:7b",
    "messages": [{"role": "user", "content": "你好"}],
    "stream": False,
})

# 生成嵌入（RAG 用）
emb = requests.post(f"{BASE}/api/embeddings", json={
    "model": "nomic-embed-text",
    "prompt": "混合专家模型",
}).json()["embedding"]
print(len(emb))       # 768

# 列出本地模型
print(requests.get(f"{BASE}/api/tags").json())
```

### 23.4.7 OpenAI 兼容 API（**最推荐**）

Ollama 提供 `/v1` 端点，前面所有章节的代码只需改 `base_url`：

```python
# 3_openai_compatible.py —— 一行切换本地模型
from openai import OpenAI

client = OpenAI(
    base_url="http://localhost:11434/v1",
    api_key="ollama",          # Ollama 不校验，随便填
)

resp = client.chat.completions.create(
    model="qwen2.5:7b",
    messages=[
        {"role": "system", "content": "你是简洁的技术助手"},
        {"role": "user", "content": "解释 PagedAttention"},
    ],
    temperature=0.2,
    max_tokens=300,
)
print(resp.choices[0].message.content)
```

**流式 + Function Calling**：

```python
# 流式
for chunk in client.chat.completions.create(
    model="qwen2.5:7b",
    messages=[{"role": "user", "content": "讲讲 MoE"}],
    stream=True,
):
    print(chunk.choices[0].delta.content or "", end="", flush=True)

# 工具调用（需模型支持；qwen2.5 / llama3.1 支持，小模型常失败）
tools = [{
    "type": "function",
    "function": {
        "name": "get_weather",
        "description": "查询城市天气",
        "parameters": {
            "type": "object",
            "properties": {"city": {"type": "string"}},
            "required": ["city"],
        },
    },
}]
r = client.chat.completions.create(
    model="qwen2.5:7b",
    messages=[{"role": "user", "content": "北京天气怎么样"}],
    tools=tools,
)
print(r.choices[0].message.tool_calls)
```

> ⚠️ **现实提醒**：7B 级本地模型的 **Function Calling 可靠性远低于 GPT-4o**。生产 Agent（第 10/17 章）若换本地模型，建议：① 选 14B+ 或专门调过工具调用的模型；② 用结构化输出 + 自解析兜底；③ 用第 16 章 Golden Set 实测工具调用正确率再上线。

---

## 23.5 本地部署大模型：完整决策流程

### 23.5.1 第一步：显存估算（决定"能不能跑"）

**核心公式**：

\[
\text{显存} \approx \underbrace{P \times q}_{\text{权重}} + \underbrace{\text{KV Cache}}_{\text{上下文}} + \underbrace{\text{激活/开销}}_{\text{约 1~2GB}}
\]

其中 \(P\) = 参数量，\(q\) = 每参数字节数（见 23.2.5）。

**速查表**（含 KV Cache 与开销，留出安全余量）：

| 模型规模 | Q4 (0.5B/参数) | Q8 (1B/参数) | FP16 (2B/参数) |
| --- | --- | --- | --- |
| 1.5B | ~1.5 GB | ~2.5 GB | ~4 GB |
| **7B** | **~5 GB** | ~9 GB | ~16 GB |
| **14B** | **~9 GB** | ~17 GB | ~30 GB |
| **32B** | **~20 GB** | ~35 GB | ~66 GB |
| 70B | ~42 GB | ~75 GB | ~145 GB |
| 235B-A22B (MoE) | ~130 GB | ~250 GB | ~480 GB |

> **呼应第 22 章**：MoE 按**总参数**估显存，不按激活参数！`A22B` 仍需上百 GB。

**KV Cache 补充估算**（长上下文时不可忽略）：

```python
# 4_vram_estimator.py —— 显存估算小工具
def estimate_vram(params_b: float, quant_bytes: float = 0.5,
                  ctx: int = 4096, layers: int = 32,
                  kv_heads: int = 8, head_dim: int = 128,
                  batch: int = 1, kv_bytes: float = 2) -> dict:
    weights_gb = params_b * quant_bytes
    kv_gb = (2 * layers * kv_heads * head_dim * ctx * batch * kv_bytes) / 1e9
    overhead_gb = 1.5
    total = weights_gb + kv_gb + overhead_gb
    return {
        "权重(GB)": round(weights_gb, 2),
        "KV Cache(GB)": round(kv_gb, 2),
        "开销(GB)": overhead_gb,
        "合计(GB)": round(total, 2),
    }

for p in [1.5, 7, 14, 32, 70]:
    print(f"{p}B Q4 4K上下文:", estimate_vram(p))
print("7B Q4 32K长上下文:", estimate_vram(7, ctx=32768))
```

### 23.5.2 第二步：硬件选型

| 显存 | 能跑什么（Q4） | 典型设备 | 体验 |
| --- | --- | --- | --- |
| 4~6 GB | 1.5B~3B、7B 勉强（部分卸载 CPU） | 老笔记本、GTX 1650 | 慢，仅体验 |
| **8 GB** | **7B 舒适**（4K~8K 上下文） | RTX 3060/4060 | 入门首选，20~40 tok/s |
| **12~16 GB** | **14B 舒适 / 7B 长上下文** | RTX 3060 12G / 4070 Ti | 甜点级，质量明显提升 |
| **24 GB** | **32B 舒适 / 14B 长上下文** | RTX 3090 / 4090 | 本地最强性价比 |
| 48~80 GB | 70B Q4 / 32B 高精度 | A6000 / A100 / H100 | 准生产 |
| **128~192 GB 统一内存** | **70B~235B MoE（慢）** | **Mac Studio M2/M3 Ultra** | 能装下大模型，速度靠带宽 |
| 仅 CPU + 大内存 | 7B/14B Q4 能跑 | 64GB DDR5 | 3~8 tok/s，仅离线批处理 |

> **Apple Silicon 的特殊优势**：统一内存让 CPU/GPU 共享全部 RAM，192GB 的 Mac Studio 能加载 235B 级 MoE（第 22 章）——这是同价位 PC 做不到的，代价是带宽较低、生成慢。

**决策树**：

```
你的硬件 → 选模型
  8G 显存   → qwen2.5:7b / llama3.1:8b (Q4)
  12~16G    → qwen2.5:14b (Q4)，中文质量跃升
  24G       → qwen2.5:32b (Q4)，接近 GPT-3.5 体验
  纯 CPU    → qwen2.5:3b (Q4) + llama.cpp，或只用嵌入模型
  Mac 128G+ → 可试 70B / Qwen3-MoE，但接受 <10 tok/s
```

### 23.5.3 第三步：三条部署路线

#### 路线 A：Ollama（个人开发 / 私有化小服务）⭐ 最简单

```bash
# 1. 装 Ollama
# 2. 拉模型 + 嵌入模型
ollama pull qwen2.5:7b
ollama pull nomic-embed-text

# 3. 跑起来（自动起服务）
ollama run qwen2.5:7b

# 4. 代码里用 OpenAI 兼容端点（见 23.4.7）
```

**适合**：本地开发、Demo、数据不能出内网的 1~5 人小团队。
**不适合**：高并发（Ollama 并发能力弱，默认 `OLLAMA_NUM_PARALLEL=1~2`）。

#### 路线 B：vLLM（生产服务 / 高并发）

```bash
pip install vllm
vllm serve Qwen/Qwen2.5-7B-Instruct \
  --port 8000 \
  --gpu-memory-utilization 0.9 \
  --max-model-len 8192 \
  --enable-prefix-caching \
  --api-key your-secret-key
```

```python
# 客户端不变，只换 base_url + api_key
client = OpenAI(base_url="http://your-server:8000/v1", api_key="your-secret-key")
```

**适合**：对外 API、几十~几百并发、需要吞吐与监控。
**要求**：NVIDIA GPU + CUDA，模型用 HF / AWQ / GPTQ 格式（非 GGUF）。

#### 路线 C：llama.cpp / GGUF（无 GPU / 边缘 / Mac）

```bash
# 编译（或使用预编译包）
git clone https://github.com/ggerganov/llama.cpp && cd llama.cpp && cmake -B build && cmake --build build -j

# 直接跑 GGUF
./build/bin/llama-cli -m models/qwen2.5-7b-q4_k_m.gguf -p "你好" -n 256

# 起 OpenAI 兼容服务
./build/bin/llama-server -m models/qwen2.5-7b-q4_k_m.gguf --port 8080 -c 4096 -ngl 999
```

**适合**：无独显、嵌入式、想完全掌控、Mac 原生优化。
**注意**：`-ngl 999` 表示全部层放 GPU；CPU 模式去掉该参数。

#### 三条路线对比

| 维度 | A. Ollama | B. vLLM | C. llama.cpp |
| --- | --- | --- | --- |
| 上手难度 | ⭐ 最简 | ⭐⭐⭐ | ⭐⭐ |
| 并发吞吐 | 低 | **最高** | 低 |
| GPU 要求 | 可选 | 必需 NVIDIA | 可选 |
| 模型格式 | 自动 GGUF | HF/AWQ/GPTQ | GGUF |
| 生产监控 | 弱 | **强**（Prometheus） | 中 |
| 典型用户 | 个人/小团队 | 服务端 | 边缘/极客 |

> **组合建议**：**开发用 Ollama，上线换 vLLM**——因为都走 OpenAI 兼容 API，切换只改 `base_url`，业务代码零改动。

### 23.5.4 第四步：部署前检查清单

- [ ] **显存估算通过**（用 23.5.1 工具，留 20% 余量）
- [ ] **磁盘空间**：GGUF 模型 3~100GB + 缓存，Windows 注意改 `OLLAMA_MODELS` 别撑爆 C 盘
- [ ] **驱动/CUDA**：`nvidia-smi` 能看到 GPU；vLLM 需匹配 CUDA 版本
- [ ] **上下文长度**：`num_ctx` 越大 KV Cache 越大，别盲目设 128K
- [ ] **嵌入模型单独拉**：RAG 必须 `ollama pull nomic-embed-text`
- [ ] **并发参数**：`OLLAMA_NUM_PARALLEL` 与显存成正比，别贪心
- [ ] **模型许可证**：Llama / Qwen / DeepSeek 各自的商用条款要确认
- [ ] **数据合规**：本地部署的核心价值是数据不出内网，确认无遥测外传
- [ ] **fallback**：本地模型挂了/超时要有降级策略

### 23.5.5 第五步：性能压测

```python
# 5_benchmark.py —— 测 TTFT / TPOT / 吞吐
import time, statistics
from openai import OpenAI

client = OpenAI(base_url="http://localhost:11434/v1", api_key="ollama")

def bench(prompt: str, n: int = 5, max_tokens: int = 128):
    ttfts, tpots, tps = [], [], []
    for _ in range(n):
        t0 = time.perf_counter()
        first = None
        count = 0
        stream = client.chat.completions.create(
            model="qwen2.5:7b",
            messages=[{"role": "user", "content": prompt}],
            max_tokens=max_tokens, stream=True, temperature=0,
        )
        for chunk in stream:
            delta = chunk.choices[0].delta.content
            if delta:
                if first is None:
                    first = time.perf_counter() - t0
                count += 1
        total = time.perf_counter() - t0
        if first is not None and count > 1:
            ttfts.append(first)
            tpots.append((total - first) / (count - 1))
            tps.append(count / total)
    return {
        "TTFT(秒)": round(statistics.mean(ttfts), 2),
        "TPOT(秒/token)": round(statistics.mean(tpots), 3),
        "吞吐(tok/s)": round(statistics.mean(tps), 1),
    }

if __name__ == "__main__":
    for p in ["你好", "请详细解释 Transformer 的注意力机制，包括 QKV 与多头"]:
        print(f"Prompt({len(p)}字):", bench(p))
```

**判读基线**（7B Q4 / RTX 4060 8G 参考）：

| 指标 | 良好 | 需排查 |
| --- | --- | --- |
| TTFT | < 1s | > 3s（模型重复加载？prefill 太长？） |
| 吞吐 | > 20 tok/s | < 10 tok/s（是否掉到 CPU 卸载？） |
| TPOT | < 0.05s | 抖动大（并发抢占 / 显存不足换页） |

用 `ollama ps` 看是否有 `CPU` 字样：

```bash
ollama ps
# NAME          SIZE    PROCESSOR   UNTIL
# qwen2.5:7b    4.7 GB  100% GPU    4 minutes from now   ← 全 GPU，好
# qwen2.5:7b    4.7 GB  60%/40% CPU/GPU                  ← 部分卸载，会慢很多
```

### 23.5.6 第六步：与前面章节集成（几乎零改造）

**① 第 10 章 Agent → 本地模型**

```python
# 6_local_agent.py —— 手写 Agent Loop 换本地模型
from openai import OpenAI
import json

client = OpenAI(base_url="http://localhost:11434/v1", api_key="ollama")

def calculator(expr: str) -> str:
    return str(eval(expr, {"__builtins__": {}}, {}))

tools = [{
    "type": "function",
    "function": {
        "name": "calculator",
        "description": "计算数学表达式",
        "parameters": {"type": "object",
                       "properties": {"expr": {"type": "string"}},
                       "required": ["expr"]},
    },
}]

def run_agent(question: str) -> str:
    messages = [{"role": "user", "content": question}]
    for _ in range(5):                       # 硬轮数上限（呼应第 20 章）
        r = client.chat.completions.create(
            model="qwen2.5:7b", messages=messages, tools=tools,
        )
        msg = r.choices[0].message
        messages.append(msg)
        if not msg.tool_calls:
            return msg.content
        for call in msg.tool_calls:
            args = json.loads(call.function.arguments)
            result = calculator(**args)
            messages.append({
                "role": "tool", "tool_call_id": call.id, "content": result,
            })
    return "达到最大轮数"

print(run_agent("123 * 456 等于多少？"))
```

**② 第 11 章 RAG → 全本地向量化**

```python
# 7_local_embeddings.py —— 用本地嵌入模型，数据完全不出内网
from openai import OpenAI
import numpy as np

client = OpenAI(base_url="http://localhost:11434/v1", api_key="ollama")

def embed(texts: list[str]) -> np.ndarray:
    r = client.embeddings.create(model="nomic-embed-text", input=texts)
    return np.array([d.embedding for d in r.data])

docs = ["MoE 通过稀疏激活降低计算成本", "PagedAttention 减少显存碎片", "Ollama 基于 llama.cpp"]
vecs = embed(docs)
q = embed(["怎么节省大模型显存？"])[0]

# 余弦相似度
sims = vecs @ q / (np.linalg.norm(vecs, axis=1) * np.linalg.norm(q))
print("最相关:", docs[int(np.argmax(sims))])
```

> **注意**：`from openai import OpenAI` + `client.embeddings.create(...)` 对 Ollama 可用；若报 404，改用原生 `/api/embeddings`（见 23.4.6）。

**③ 第 16 章评测 → 验证本地模型是否够用**

换模型前务必用 Golden Set 跑分对比，**特别测 Function Calling 与中文指令遵循**——这是本地小模型最容易翻车的地方。

### 23.5.7 第七步：生产化

| 关注点 | 做法 |
| --- | --- |
| **多模型路由** | 简单问答走 7B、代码走 coder、复杂推理走 32B，用轻量分类器分流省成本 |
| **监控** | vLLM 自带 Prometheus `/metrics`；Ollama 需自己包一层 FastAPI 记日志 |
| **限流** | 本地 GPU 并发有限，用信号量控制，超出排队，避免 OOM |
| **超时/降级** | 本地超时 → 降级到云端 API 或返回友好提示 |
| **缓存** | 相同 prompt 结果缓存；启用 prefix caching 复用长 system prompt |
| **安全** | 绑定内网地址、加 API Key（vLLM `--api-key`）、关掉 unnecessary 端点 |
| **模型更新** | `ollama pull` 更新后跑回归 Golden Set，防止质量悄悄退化 |

---

## 23.6 实战：一键搭建"私有化 RAG 服务"

把 Ollama + 向量检索 + FastAPI 组装成一个数据不出内网的问答服务。

```python
# 8_private_rag.py —— 私有化 RAG 服务（可直接运行）
# 前置: ollama pull qwen2.5:7b && ollama pull nomic-embed-text
#       pip install fastapi uvicorn numpy pydantic requests
import numpy as np
import requests
from fastapi import FastAPI
from pydantic import BaseModel

OLLAMA = "http://localhost:11434"
CHAT_MODEL = "qwen2.5:7b"
EMB_MODEL = "nomic-embed-text"

app = FastAPI(title="私有化 RAG 服务")

class AskRequest(BaseModel):
    question: str
    top_k: int = 2

class AddRequest(BaseModel):
    text: str

# 简易内存向量库（生产换 Chroma / pgvector，见第 11 章）
documents: list[str] = []
vectors: list[np.ndarray] = []

def embed(text: str) -> np.ndarray:
    r = requests.post(
        f"{OLLAMA}/api/embeddings",
        json={"model": EMB_MODEL, "prompt": text}, timeout=60,
    )
    r.raise_for_status()
    return np.array(r.json()["embedding"], dtype=np.float32)

def generate(prompt: str) -> str:
    r = requests.post(
        f"{OLLAMA}/api/generate",
        json={"model": CHAT_MODEL, "prompt": prompt, "stream": False,
              "options": {"temperature": 0.1}},
        timeout=180,
    )
    r.raise_for_status()
    return r.json()["response"]

@app.post("/add")
def add_doc(req: AddRequest):
    vectors.append(embed(req.text))
    documents.append(req.text)
    return {"ok": True, "total": len(documents)}

@app.post("/ask")
def ask(req: AskRequest):
    if not documents:
        return {"answer": "知识库为空，请先 /add"}
    q = embed(req.question)
    sims = np.array([float(v @ q / (np.linalg.norm(v) * np.linalg.norm(q)))
                     for v in vectors])
    idx = np.argsort(-sims)[:req.top_k]
    context = "\n".join(f"- {documents[i]}" for i in idx)

    prompt = f"""仅根据以下资料回答问题，资料没提到就说"资料中未提及"。

资料：
{context}

问题：{req.question}
答案："""
    return {
        "answer": generate(prompt),
        "sources": [documents[i] for i in idx],
        "scores": [round(float(sims[i]), 3) for i in idx],
    }

@app.get("/health")
def health():
    return {"docs": len(documents), "chat_model": CHAT_MODEL, "emb_model": EMB_MODEL}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=8080)
```

**运行**：

```bash
python 8_private_rag.py

# 另一个终端
curl -X POST http://127.0.0.1:8080/add -H "Content-Type: application/json" -d "{\"text\":\"MoE 用稀疏激活降低计算成本，但显存仍需装下全部专家\"}"
curl -X POST http://127.0.0.1:8080/add -H "Content-Type: application/json" -d "{\"text\":\"PagedAttention 借鉴虚拟内存分页，减少 KV Cache 碎片\"}"
curl -X POST http://127.0.0.1:8080/ask -H "Content-Type: application/json" -d "{\"question\":\"MoE 省显存吗？\"}"
```

**响应示意**：

```json
{
  "answer": "不省。资料指出 MoE 通过稀疏激活降低计算成本，但显存仍需装下全部专家权重。",
  "sources": ["MoE 用稀疏激活降低计算成本..."],
  "scores": [0.812]
}
```

> 这个例子把第 6 章（FastAPI）、第 11 章（RAG）、第 19 章（LLM）、第 22 章（MoE）和本章串起来了——**数据、检索、生成全部在内网完成**。

---

## 23.7 常见踩坑

| 坑 | 症状 | 解决 |
| --- | --- | --- |
| **C 盘爆满** | 拉几个模型几十 GB 没了 | Windows 设 `OLLAMA_MODELS` 到 D 盘（**拉模型之前**设） |
| **模型反复加载** | 每次请求 TTFT 好几秒 | `OLLAMA_KEEP_ALIVE=30m` 或 `-1` 常驻 |
| **掉到 CPU 卸载** | `ollama ps` 显示 `X% CPU/GPU`，速度暴跌 | 换更小模型 / 更小量化 / 减小 `num_ctx` |
| **OOM / 服务崩** | 并发一高就挂 | 降 `OLLAMA_NUM_PARALLEL`、降 `num_ctx`、加应用层信号量 |
| **中文乱码 / 答非所问** | 输出奇怪、不遵循中文指令 | 别手改 `TEMPLATE`；用官方模型；确认 chat 端点而非 generate 端点 |
| **工具调用失败** | Agent 不返回 `tool_calls` | 换 14B+ / coder 模型；改结构化输出自解析；Golden Set 实测 |
| **长上下文反而崩** | 设 128K 后直接 OOM | KV Cache 随长度线性涨，按显存反推合理 `num_ctx` |
| **用聊天模型做嵌入** | RAG 检索效果极差 | 必须 `ollama pull nomic-embed-text` 等专用嵌入模型 |
| **误以为本地模型 = 云端能力** | 复杂推理/多 Agent 效果差 | 小模型能力有上限，用第 16 章评测量化，必要时混合路由 |
| **GGUF 喂给 vLLM** | 加载报错 | vLLM 要 HF/AWQ/GPTQ；GGUF 走 Ollama/llama.cpp |
| **局域网访问不了** | 别的机器连不上 | `OLLAMA_HOST=0.0.0.0:11434` + 防火墙放行 + 注意安全 |
| **MoE 显存误判** | 以为 A22B 只要 22B 显存 | 按总参数估（第 22 章） |
| **许可证风险** | 商用被投诉 | 部署前确认各模型 License（Llama/Qwen/DeepSeek 条款不同） |

---

## 23.8 面试高频问题

**Q1. vLLM 为什么比裸 `transformers.generate()` 快？**

A：三个核心：① **PagedAttention** 把 KV Cache 分页，消除显存碎片，并发提升 2~4 倍；② **Continuous Batching** 让完成一个请求就立刻插入新请求，GPU 不空转；③ 配合 FlashAttention、量化、TP/EP 并行。其中 continuous batching 是吞吐提升的首要来源。

**Q2. Prefill 和 Decode 有什么区别？对应什么指标？**

A：Prefill 一次性处理整个 Prompt（计算密集），决定 **TTFT**；Decode 逐 Token 生成（访存密集，复用 KV Cache），决定 **TPOT**。优化手段不同：prefill 靠算力与 chunked prefill，decode 靠量化与访存带宽、投机解码。

**Q3. 7B 模型 Q4 量化大概要多少显存？**

A：权重 7B × 0.5B ≈ 3.5GB，加上 KV Cache（4K 上下文约 0.5~2GB，视 GQA 而定）与框架开销 1~2GB，**实际约 5~6GB**，8G 显卡可舒适运行。注意长上下文时 KV Cache 会显著增长。

**Q4. Ollama 和 llama.cpp / vLLM 是什么关系？**

A：Ollama 是 **llama.cpp 的上层封装**（模型管理 + 服务 + API + 量化分发），主打本地体验；vLLM 是独立实现，面向 GPU 高并发生产。三者都提供 OpenAI 兼容 API，因此业务代码可无缝切换。

**Q5. 本地部署大模型，最常被低估的成本是什么？**

A：**显存而非算力**，尤其是 MoE 模型按总参数占显存（第 22 章）；以及 **KV Cache 随上下文/并发线性增长**。此外常被忽略的是本地小模型的 **Function Calling / 指令遵循能力下降**，需要用评测集验证而非假设等价。

**Q6. 如何让已有 Agent 代码切到本地模型？**

A：所有主流框架都提供 OpenAI 兼容端点，把 `OpenAI(base_url=..., api_key=...)` 指到本地即可，其余代码不变。但要额外做三件事：① 用 Golden Set 回归质量（尤其工具调用）；② 加超时/降级；③ 控制并发避免 OOM。

**Q7. 什么时候该用 vLLM 而不是 Ollama？**

A：需要**高并发（几十以上）、吞吐指标、Prometheus 监控、生产 SLA** 时用 vLLM；个人开发、原型、1~5 人内网小服务用 Ollama 更快落地。推荐"开发 Ollama、上线 vLLM"，靠 OpenAI 兼容 API 保持零改造切换。

---

## 23.9 本章小结

| 你学到了 | 对应技能点 |
| --- | --- |
| 推理 ≠ 训练 | memory-bound、TTFT / TPOT / 吞吐指标 |
| 核心技术 | KV Cache、PagedAttention、连续批处理、量化、投机解码、TP/EP |
| 六大框架选型 | vLLM / SGLang / TGI / TRT-LLM / llama.cpp / Ollama + 决策树 |
| Ollama 实战 | 安装、命令、量化命名、Modelfile、REST API、OpenAI 兼容端点 |
| 显存估算 | 公式 + 可运行估算工具 + 硬件对照表 |
| 三条部署路线 | Ollama（个人）/ vLLM（生产）/ llama.cpp（边缘） |
| 部署检查清单 | 显存/磁盘/驱动/上下文/嵌入模型/许可证/降级 |
| 压测与监控 | TTFT/TPOT/吞吐脚本、`ollama ps` 排查 |
| 与前面章节集成 | Agent / RAG / 评测换 base_url 即可 |
| 私有化 RAG 实战 | FastAPI + 本地嵌入 + 本地生成，数据不出内网 |

**核心心法**：

> 本地部署的本质不是"把模型下载下来"，而是
> **① 按总参数估显存 → ② 按并发选框架 → ③ 用 OpenAI 兼容 API 保持业务零改造 → ④ 用评测集验证本地模型真的够用**。

**应用与基建闭环**：从第 1 章语法，到第 10~21 章 Agent 全栈与工程化，到第 22 章理解模型架构，到本章把模型**跑在自己的机器上**——语法 → 应用 → 智能 → 基建已形成闭环。

**还想往模型本身再走一步？** 继续第 24 章《从零开发大模型》（PyTorch 手写 GPT + 预训练全流程）与第 25 章《大模型调优实战》（SFT / LoRA·QLoRA / DPO / 评测回归），把「**建模型 / 调模型**」也纳入体系：训练出的权重正是本章部署的输入，两者首尾相接。
