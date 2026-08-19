# 第十五章 多模态语音 Agent：让 Agent"能听、会说、看得见"

> 本章目标：把第 10 章的文本 Agent 扩展到**多模态**——语音（STT 听、TTS 说、Realtime 实时对话）与图像（理解与生成）。先讲清多模态 Agent 的形态与架构，再逐块掌握 OpenAI 语音/图像 API，最后完成一个**可运行的语音问答助手**（麦克风录音 → 语音转文字 → LLM 回答 → 文字转语音播放）。
>
> 前置知识：第 10 章（Agent 核心）、第 4 章（asyncio/流式）。对标 Java：WebRTC、Java 语音服务端（语音识别/合成 SDK）。
>
> 环境准备：`pip install openai sounddevice numpy`，设置 `OPENAI_API_KEY`。（录音部分也可用任意 wav/mp3 文件替代麦克风。）

## 15.1 多模态 Agent：从"纯文本"到"全感官"

### 15.1.1 三种多模态形态

| 形态 | 输入 → 输出 | 例子 |
| --- | --- | --- |
| **输入多模态** | 语音/图片/视频 → 文本 | 会议纪要、图片问答、语音助手 |
| **输出多模态** | 文本 → 语音/图片/视频 | 语音播报、AI 绘图、视频生成 |
| **全双工** | 语音 ↔ 语音 实时 | 打电话式 AI（Realtime API） |

本模块主线：**听（STT）→ 想（LLM/Agent）→ 说（TTS）**，再补充"看得见（图像）"。

### 15.1.2 语音 Agent 经典架构

```
┌────────────┐ 音频流 ┌───────────┐  文本   ┌────────────┐  文本  ┌───────────┐  音频  ┌────────────┐
│  麦克风     │ ──────► │  STT 转写   │ ──────► │ LLM/Agent   │ ─────► │ TTS 合成    │ ─────► │  扬声器     │
│  (用户说)   │        │ (语音→文字) │        │ (理解/工具) │        │ (文字→语音) │        │ (AI 说)     │
└────────────┘        └───────────┘        └────────────┘        └───────────┘        └────────────┘
```

两种实现路线：

| 路线 | 方式 | 延迟 | 适合 |
| --- | --- | --- | --- |
| **管道式** | STT → LLM → TTS 三段接力 | 秒级 | 常见业务、工具集成（本章主线） |
| **Realtime** | 一个 WebSocket 全双工，音频直进直出 | 毫秒级 | 自然对话、打断、情感表达 |

> **对照 Java**：管道式 ≈ 同步 REST 三段调用；Realtime ≈ WebSocket 长连接（第 4 章 asyncio 思想）。AI 语音的工程核心永远是**延迟**——每省 100ms，体验上一个台阶。

## 15.2 STT：让 Agent 听懂（语音转文字）

OpenAI 语音转文字 API（`audio.transcriptions`）支持 `whisper-1`（经典）与新一代 `gpt-4o-transcribe` / `gpt-4o-mini-transcribe`（带 prompt 提示词、可输出带时间戳 JSON）。

```python
# 1_stt.py —— 语音转文字
from openai import OpenAI

client = OpenAI()

with open("voice.wav", "rb") as f:          # 任意 16k 采样以上音频（wav/mp3/m4a…）
    resp = client.audio.transcriptions.create(
        model="gpt-4o-mini-transcribe",     # 便宜；精度要求高用 gpt-4o-transcribe/whisper-1
        file=f,
        response_format="json",              # json/text/verbose_json
    )
print("转写结果：", resp.text)
```

**STT 生产要点**：

| 要点 | 说明 |
| --- | --- |
| 采样率 | 转写前统一到 16kHz（常见工具链默认），格式用 wav/mp3 |
| **prompt 热词** | 传领域词（"退款、工单号 ODxxxx"）显著提升专有名词识别 |
| 长音频 | 分段转写（每段 <25MB），再拼接；或用 verbose_json 拿时间戳对齐 |
| 中文 | 默认支持，无需额外语言参数 |

## 15.3 TTS：让 Agent 开口（文字转语音）

```python
# 2_tts.py —— 文字转语音并保存播放
from openai import OpenAI

client = OpenAI()

resp = client.audio.speech.create(
    model="gpt-4o-mini-tts",                # 新一代；也可 tts-1（经典）
    voice="alloy",                           # alloy/echo/fable/onyx/nova/shimmer
    input="您好，我是智能客服小助手，很高兴为您服务。",
    speed=1.0,                               # 语速 0.25~4.0
    response_format="mp3",
)
resp.write_to_file("reply.mp3")
print("已生成 reply.mp3")

# 流式合成：边生成边播放，显著降低首音延迟
with client.audio.speech.with_streaming_response.create(
    model="gpt-4o-mini-tts", voice="alloy", input="这是流式语音。"
) as s:
    s.stream_to_file("reply_stream.mp3")
```

**TTS 选择**：固定客服播报用 `gpt-4o-mini-tts`（便宜、自然）；情感/多语气用 `tts-1-hd` 或配音专用模型；**voice 定好后不要频繁换**（用户有"声音记忆"）。

## 15.4 Realtime API：全双工实时对话

管道式有"听到你说完才回答"的天然延迟。Realtime 让模型**边听边想边说**，支持打断（barge-in）、流式事件：

```python
# 3_realtime.py —— Realtime 实时对话（GPT-4o Realtime，WebSocket 全双工）
import asyncio
from openai import OpenAI, Realtime

async def main():
    client = OpenAI()
    realtime = Realtime(                              # 新一代 SDK 的实时客户端
        model="gpt-4o-realtime",                      # 音频直进直出模型
        instructions="你是实时语音助手，回答简短口语化。",
    )
    realtime.connect()                                # 建立 WebSocket
    # 音频输入（麦克风或文件流）与音频输出（扬声器）都由 SDK 管理
    realtime.send_text("你好，介绍一下你自己")
    print(realtime.recv_text())                       # 等待文字回显

asyncio.run(main())
```

> Realtime 的事件流（`session.created` / `conversation.item` / `response.output_audio`…）就是"Agent Loop 的音频版"——第 10 章的 messages 在这里变成**会话事件流**。生产实现打断、唤醒词、VAD 检测需要围绕事件循环做状态机（比纯文本复杂得多，优先用官方 SDK 封装）。

## 15.5 图像多模态：让 Agent"看得见"

除了语音，视觉是另一大模态——输入理解与输出生成：

```python
# 4_vision.py —— 图像理解 + 图像生成
from openai import OpenAI

client = OpenAI()

# ── 输入：看图说话（传入 base64 或 URL）──
import base64
with open("receipt.jpg", "rb") as f:
    img_b64 = base64.b64encode(f.read()).decode()

resp = client.chat.completions.create(
    model="gpt-4o-mini",
    messages=[
        {"role": "user", "content": [
            {"type": "text", "text": "这张小票总计多少钱？列出商品明细。"},
            {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{img_b64}"}},
        ]},
    ],
)
print("小票识别：", resp.choices[0].message.content)

# ── 输出：文生图 ──
img = client.images.generate(
    model="gpt-image-1",
    prompt="一只戴耳机的柴犬，赛博朋克风格",
    size="1024x1024", n=1,
)
print("图片 URL：", img.data[0].url)      # 或 b64_json 直接保存
```

**图像多模态在生产 Agent 里的典型玩法**：

| 场景 | 做法 |
| --- | --- |
| 工单附图审核 | 图像理解提取"型号/破损情况"结构化字段 |
| 截图 OCR + 语义 | 视觉模型直接读懂界面截图（比 OCR 更聪明） |
| 商品图生成 | 文生图补素材、风格化 |
| 多模态 RAG | 图片也向量化（第 11 章扩展到图像） |

## 15.6 实战：语音问答助手（完整可运行）

**管道式语音助手**：录音 → STT → LLM（含工具）→ TTS → 播放。录音部分用 wav 文件替代麦克风，保证任意环境可跑：

```python
# 5_voice_assistant.py —— 语音问答助手：STT + Agent + TTS 完整闭环
from openai import OpenAI

client = OpenAI()

# ── 1. STT：把语音文件变成文字 ──
def listen(audio_path: str) -> str:
    with open(audio_path, "rb") as f:
        resp = client.audio.transcriptions.create(
            model="gpt-4o-mini-transcribe", file=f,
            prompt="客服对话：订单、退款、物流。",          # 热词提升准确率
        )
    return resp.text

# ── 2. LLM：用第 10 章的 Function Calling 模式做"语音版 Agent" ──
def get_order(order_id: str) -> str:
    return {"OD2026081901": "已发货", "OD2026081902": "待付款"}.get(
        order_id, f"未找到 {order_id}")

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

def think(text: str) -> str:
    messages = [{"role": "user", "content": text}]
    for _ in range(3):
        resp = client.chat.completions.create(
            model="gpt-4o-mini", messages=messages, tools=TOOLS)
        msg = resp.choices[0].message
        messages.append(msg)
        if not msg.tool_calls:
            return msg.content or "（无回复）"
        for tc in msg.tool_calls:
            import json
            args = json.loads(tc.function.arguments)
            messages.append({"role": "tool", "tool_call_id": tc.id,
                             "content": FUNCS[tc.function.name](**args)})
    return "抱歉，我处理不了。"

# ── 3. TTS：把回答变成语音 ──
def speak(text: str, out: str = "reply.mp3"):
    client.audio.speech.create(
        model="gpt-4o-mini-tts", voice="alloy", input=text,
    ).write_to_file(out)
    print(f"已生成语音：{out}（播放它即可听到）")

if __name__ == "__main__":
    # 演示：用预先准备的语音文件（可用任意录音，或下载示例语音）
    import os, subprocess, sys

    demo_audio = "question.wav"
    if not os.path.exists(demo_audio):
        # 没有录音文件时：用系统 TTS 生成一个演示问题音频（Windows）
        if sys.platform == "win32":
            import win32com.client  # pip install pywin32
            spk = win32com.client.Dispatch("SAPI.SpVoice")
            spk.SaveToFile("我的订单 OD2026081901 到哪里了？", demo_audio)
        else:
            print("请准备一个 question.wav 语音文件后重跑")
            sys.exit(1)

    question = listen(demo_audio)             # 听
    print("识别到：", question)
    answer = think(question)                  # 想（可调用工具）
    print("回答：", answer)
    speak(answer)                             # 说
```

**运行链路**：`question.wav` → 识别"我的订单 OD2026081901 到哪里了？" → LLM 调用 `get_order` 工具 → 生成回答 → 合成 `reply.mp3`。把 `think()` 换成第 10 章的 `Runner.run_sync` 多 Agent 或第 12 章 LangGraph 图，就是完整的语音 Agent。

## 15.7 工程、延迟与合规

| 维度 | 要点 |
| --- | --- |
| **延迟优化** | 优先流式（TTS 边生边播、STT 流式转写）；预连接；模型选 mini |
| **中断处理** | Realtime 用 `response.cancel` 实现打断；管道式需 VAD 检测 |
| **音频处理** | 采样率统一、响度归一、静音裁剪（降低 STT 成本与错误） |
| **合规** | 录音需用户知情同意；语音属于个人信息，脱敏与存储期限按法规（呼应第 5 章合规红线） |
| **成本** | 按音频时长计费：先 VAD 只转有效语音段，省一半以上费用 |

> **安全红线**：语音 Agent 容易被"越权指令"攻击（声音冒充、诱导）。身份敏感场景必须**声纹/多因素校验**，且把 STT 文本当不可信输入（第 10.9 的注入防御同样适用）。

## 15.8 练习与面试

### 本章练习

1. 用你自己的录音（或系统 TTS 生成）跑通 15.2 STT，对比 `whisper-1` 与 `gpt-4o-mini-transcribe` 的准确率；
2. 给 15.2 的 STT 加 `prompt` 热词（你的名字/产品名），对比识别效果差异；
3. 用 15.3 生成三种不同 `voice` 的同句话，播放对比音色差异；
4. 把 15.6 的 `think()` 换成 OpenAI Agents SDK 多 Agent（第 10 章），实现"语音客服主管"；
5. 用 15.5 的图像理解识别一张截图，提取其中表格数据为 JSON；
6. 给 15.6 增加"图像理解"工具（上传小票图片→提取金额），语音+视觉双模态；
7. 选做：用 `sounddevice` 实现麦克风实时录音（边录边存），替换演示音频文件。

### 面试题

| 问题 | 要点 |
| --- | --- |
| 多模态 Agent 的三种形态？ | 输入多模态、输出多模态、全双工 |
| 语音 Agent 的基本链路？ | STT → LLM/Agent → TTS |
| 管道式 vs Realtime 区别？ | 三段接力（秒级延迟）vs 全双工 WebSocket（毫秒级、可打断） |
| STT 怎么提升准确率？ | prompt 热词、高质量音频、采样率统一、分段 |
| TTS 怎么降低延迟？ | 流式合成边生边播、voice 复用、模型选 mini |
| Realtime API 是什么？ | 音频直进直出的实时对话模型，事件流=Agent Loop 音频版 |
| 图像理解怎么用？ | chat.completions 传 image_url（base64/URL）+ text 指令 |
| 文生图用什么 API？ | images.generate（gpt-image 系列），size/n 控制 |
| 语音 Agent 延迟怎么优化？ | 流式、预连接、VAD 只转有效段、mini 模型 |
| 语音数据合规注意什么？ | 知情同意、个人信息脱敏、存储期限 |
| 语音 Agent 的安全风险？ | 声音冒充、越权指令；声纹校验 + 不可信输入处理 |
| STT 成本怎么省？ | VAD 静音裁剪、只转有效段、按需选模型 |
| 多模态怎么和 RAG 结合？ | 图片/音频向量化入库，检索多模态内容 |
| Java 侧怎么做语音 Agent？ | WebRTC + 语音服务端；原理与本章一致 |

### 本章小结

- **多模态形态**（15.1）：输入/输出/全双工三种；管道式与 Realtime 两条路线；
- **STT**（15.2）：`gpt-4o-transcribe` 家族、热词 prompt、长音频分段；
- **TTS**（15.3）：`gpt-4o-mini-tts`、voice 选择、流式合成；
- **Realtime**（15.4）：WebSocket 全双工、事件流 = Agent Loop 音频版；
- **图像**（15.5）：视觉理解（base64 传图）+ 文生图；
- **实战**（15.6）：语音问答助手完整闭环，可直接替换为多 Agent；
- **工程合规**（15.7）：延迟优化、VAD 省钱、录音合规与安全红线。

---

## 🚀 后续进阶方向

- **全双工产品化**：VAD + 唤醒词 + 打断状态机（Realtime 事件驱动）；
- **多模态输入**：视频理解（帧提取 + 视觉模型）、PDF/表格识别；
- **语音 Agent 与工具**：语音 → Agent 工具循环 → 语音播报结果（订餐/查询客服）；
- **多模态 RAG**：图像/语音向量化（第 11 章扩展）。

配套：下一章 [16-Agent评测体系.md](./16-Agent评测体系.md)（语音/多模态 Agent 同样需要评测，质量不能靠感觉）｜ [README.md](./README.md)
