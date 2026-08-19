# 第十一章 RAG 与向量数据库：让 Agent 学会"查资料"

> 本章目标：解决 LLM 的"知识三缺陷"（时效性、私有数据、幻觉），掌握 **RAG（Retrieval-Augmented Generation，检索增强生成）** 完整技术栈——从 Embedding 向量化原理、向量检索，到本地向量数据库（Chroma）与生产级检索优化（切块/元数据/混合检索/Rerank/评估），最后完成一个**可运行的本地文档问答助手**。这是第 10 章 Agent 的"知识增强"扩展章。
>
> 前置知识：第 10 章（openai SDK 与 Agents）、第 7 章（NumPy 向量化）、第 6 章（Pydantic）。对标 Java 体系中的 Elasticsearch 全文检索与 MySQL 索引思想。
>
> 环境准备：`pip install openai chromadb numpy`，并设置 `OPENAI_API_KEY`。

## 11.1 为什么需要 RAG

### 11.1.1 LLM 的三大知识缺陷

| 缺陷 | 表现 | 举例 |
| --- | --- | --- |
| **时效性** | 训练数据有截止日期 | 问"今天的天气/最新股价"，它不知道 |
| **私有性** | 看不到你的内部数据 | 问"我这个仓库里有哪些订单"，它没见过 |
| **幻觉** | 不知道就编 | 编造不存在的 API、法条、订单号 |

传统解法都各有局限：

| 方案 | 做法 | 优点 | 缺点 |
| --- | --- | --- | --- |
| **重新训练/微调** | 用新数据改模型权重 | 能力内化 | 贵（GPU 成本高）、慢、无法实时更新 |
| **长上下文硬塞** | 把文档全塞进 prompt | 简单 | token 贵、超长后注意力下降（"lost in the middle"） |
| **RAG** ✅ | 先检索相关内容，再让模型基于材料回答 | 实时更新、可控、可溯源、成本低 | 需要构建检索系统 |

**RAG 的核心理念**：不把知识塞进模型，而是**按需取回**。回答问题时先从外部知识库检索相关片段，拼进 prompt，让模型"开卷考试"。

### 11.1.2 RAG vs 微调：什么时候用哪个

```
需要更新知识/引用来源/实时数据 ──► RAG（首选）
需要改变模型风格/领域能力/格式 ──► 微调（如：学会公司文档格式）
两者可组合：先 RAG 取数 + 微调过的模型生成
```

> **一句话**：RAG 是"给它查资料的能力"，微调是"改变它的思维方式"。绝大多数业务问答场景 RAG 是性价比最高的起点。

## 11.2 RAG 三段式流水线总览

```
        ┌────────── 离线：索引（建库） ──────────┐
        │  文档 → 切块(Chunk) → 向量化(Embedding) → 写入向量库   │
        └────────────────────────────────────────────┘
                          ▲ 只做一次，文档更新时重跑
        ┌────────── 在线：检索 + 生成 ──────────┐
        │  用户提问 → 向量化 → 向量库检索 Top-K → 拼装 Prompt → LLM 生成  │
        └────────────────────────────────────────────┘
```

| 阶段 | 输入 | 输出 | 关键点 |
| --- | --- | --- | --- |
| **索引（Index）** | 原始文档 | 向量库 | 切块策略 + Embedding 质量决定检索上限 |
| **检索（Retrieve）** | 用户问题 | Top-K 相关片段 | 相似度算法 + 元数据过滤 + 可选 Rerank |
| **生成（Generate）** | 问题 + 检索片段 | 最终答案 | Prompt 约束"基于材料回答，不编造" |

> **检索上限定律**：RAG 的质量天花板是**检索阶段**——检索不到相关片段，模型再聪明也没用（Garbage In, Garbage Out）。所以本章一半篇幅都在讲"如何检索得准"。

## 11.3 Embedding：把文字变成向量

### 11.3.1 原理：语义坐标

**Embedding（嵌入）** 把一段文字映射为一个高维向量（如 1536 维），使得**语义相近的文字，向量距离也近**：

```
"我想退掉昨天买的手机"   ──►  [0.02, -0.15, 0.83, ...]   ╮
"申请退款"               ──►  [0.03, -0.14, 0.81, ...]   ╯ 距离近（语义相关）
"今天天气不错"           ──►  [-0.41, 0.22, -0.07, ...]    距离远（无关）
```

与关键字搜索的本质区别：

| | 关键字搜索（BM25/ES） | 向量搜索（Embedding） |
| --- | --- | --- |
| 匹配依据 | 字面词出现 | 语义含义 |
| "手机" vs "智能手机" | 不匹配 | 高度相似 |
| 同义词/口语化 | 失效 | 有效 |
| 精确匹配（订单号） | 擅长 | 不擅长（仍可存元数据兜底） |

### 11.3.2 用 OpenAI Embedding API 生成向量

```python
# 1_embedding.py
from openai import OpenAI

client = OpenAI()

resp = client.embeddings.create(
    model="text-embedding-3-small",          # 便宜好用；大需求可用 text-embedding-3-large
    input=["我想退掉昨天买的手机", "申请退款", "今天天气不错"],
    # dimensions=512,                        # 可选：降维省存储（small 默认 1536 维）
)
vectors = [item.embedding for item in resp.data]
print("向量维度：", len(vectors[0]))

# 用 NumPy（第 7 章）算余弦相似度
import numpy as np

def cosine(a, b):
    a, b = np.array(a), np.array(b)
    return float(a @ b / (np.linalg.norm(a) * np.linalg.norm(b)))

print("手机 vs 退款：", round(cosine(vectors[0], vectors[1]), 4))   # ≈ 0.75+
print("手机 vs 天气：", round(cosine(vectors[0], vectors[2]), 4))   # 明显更低
```

> **对比 Java/ES**：Embedding 相似度 ≈ Elasticsearch 的 `knn` 检索（`dense_vector` 字段）。只不过 Java 侧把向量当作 ES 字段存，Python 侧可以本地向量库或任意数组。**向量是"数字坐标"，存哪里、怎么存都行**——理解这一点，向量数据库就只是个"带相似度搜索的存储"。

## 11.4 向量数据库选型

### 11.4.1 为什么需要专门的向量库

向量检索本质是"**在 N 个高维向量里找最近的 K 个**"（ANN，近似最近邻）。数据量上来后暴力遍历太慢，需要索引结构（HNSW/IVF 等）。这些是向量库的核心价值，其余（持久化、过滤、分布式）都是加分项。

### 11.4.2 主流选型对比

| 库 | 定位 | 部署 | 适合场景 |
| --- | --- | --- | --- |
| **Chroma** | 轻量嵌入式 | 进程内/本地文件 | **学习/原型/中小规模**（本章实战用它） |
| **FAISS** | 向量计算库（不是数据库） | 内存 | 纯检索性能、科研 |
| **pgvector** | PostgreSQL 扩展 | 已有 PG | 不想引入新组件，SQL 无缝混合检索 |
| **Qdrant / Milvus** | 专业向量数据库 | 独立服务 | 生产大规模、高并发、分布式 |
| **Elasticsearch** | 全文检索 + 向量 | 独立服务 | 已有 ES，需要关键字+向量混合 |

> **选型口诀**：学习用 Chroma；已有 PG 用 pgvector；已有 ES 用 ES 的 dense_vector；真正大流量再上 Qdrant/Milvus。

## 11.5 手写最小 RAG（不依赖任何框架）

先把原理吃透——用 NumPy 自己算相似度，20 行内跑通"开卷考试"：

```python
# 2_mini_rag.py —— 手写最小 RAG：完全理解检索+生成
from openai import OpenAI
import numpy as np

client = OpenAI()

# ── 1. 知识库（生产中是文档切块后的片段，这里是演示）──
KNOWLEDGE = [
    "退款条件：商品已签收且金额不超过 200 元可申请退款。",
    "发货时效：现货订单 24 小时内发货，预售订单 7 天内发货。",
    "优惠券：满 199 减 30，每个订单限用一张。",
    "会员等级：年消费满 5000 元升级为黄金会员，享 9 折。",
]

# ── 2. 离线索引：给每个知识片段生成向量 ──
def embed(texts: list[str]) -> np.ndarray:
    resp = client.embeddings.create(model="text-embedding-3-small", input=texts)
    return np.array([d.embedding for d in resp.data])

kb_vectors = embed(KNOWLEDGE)

# ── 3. 在线：检索（余弦相似度取 Top-K）──
def retrieve(query: str, k: int = 2) -> list[str]:
    qv = embed([query])[0]
    scores = [float(qv @ v / (np.linalg.norm(qv) * np.linalg.norm(v))) for v in kb_vectors]
    top = np.argsort(scores)[::-1][:k]           # 降序取前 k 个下标
    print("  检索命中：", [round(scores[i], 4) for i in top])
    return [KNOWLEDGE[i] for i in top]

# ── 4. 生成：把检索片段拼进 prompt，"开卷考试" ──
def ask(query: str) -> str:
    hits = retrieve(query)
    prompt = f"""你是客服。只能根据下面的【知识片段】回答，片段里没有的信息就回答"资料中没有提到"。
【知识片段】
{chr(10).join('- ' + h for h in hits)}

【问题】{query}
"""
    resp = client.chat.completions.create(
        model="gpt-4o-mini", messages=[{"role": "user", "content": prompt}]
    )
    return resp.choices[0].message.content

if __name__ == "__main__":
    print("A:", ask("我买的东西能不能退款？"))          # 命中退款条件
    print("B:", ask("黄金会员有什么折扣？"))             # 命中会员等级
    print("C:", ask("你们公司电梯密码是什么？"))         # 检索不到 → 拒绝编造
```

运行结果演示三个核心价值：**命中相关片段 → 准确回答**；**无关问题 → 检索不到 → 模型拒绝编造（缓解幻觉）**。

> 这个最小版本已经具备 RAG 的全部骨架。剩余工程化痛点——切块、持久化、元数据过滤、混合检索、Rerank——是 11.6 的内容。

## 11.6 生产级 RAG 工程要点

### 11.6.1 切块（Chunking）：检索质量的源头

文档不能整篇向量化（太长语义被稀释），需要切成有意义的块：

| 切块策略 | 做法 | 适用 |
| --- | --- | --- |
| 固定长度 | 每 512 token 一刀（重叠 50） | 通用兜底 |
| 段落切分 | 按空行/标题切 | 结构化文档 |
| 语义切分 | 按语义边界（句号/分块模型） | 长文档、精度要求高 |

```python
def split_by_paragraph(text: str, max_len: int = 500) -> list[str]:
    chunks, cur = [], ""
    for para in text.split("\n\n"):
        if len(cur) + len(para) > max_len and cur:
            chunks.append(cur); cur = para
        else:
            cur += "\n\n" + para if cur else para
    if cur:
        chunks.append(cur)
    return chunks
```

**两条铁律**：① 块太小失去上下文、太大稀释语义，**500~1000 token 是常见区间**；② 切块必须**保留元数据**（来源文件、页码、章节），回答才能溯源。

### 11.6.2 元数据过滤：检索的"SQL WHERE"

向量搜索只能比相似度，业务过滤（只看某个部门/时间范围/文档类型）要靠元数据：

```
检索时：similarity 过滤 + metadata 过滤 同时生效
问题"上一季度的订单退款政策" ──► 先按部门/时间过滤，再按语义找
```

### 11.6.3 混合检索 + Rerank：精确与语义兼得

| 手段 | 解决什么 | 做法 |
| --- | --- | --- |
| **混合检索** | 向量不擅长精确匹配（订单号、型号） | BM25 关键字 + 向量两路召回，分数加权合并 |
| **Rerank（重排）** | 粗召回 Top-50 可能夹带噪声 | 用交叉编码器（cross-encoder）对"问题-片段"逐对打分，重排取 Top-5 |

```
第一轮召回（快、粗）：BM25 + 向量  →  Top-50
第二轮重排（慢、准）：Rerank 模型   →  Top-5  → 送进 Prompt
```

### 11.6.4 评估：RAG 不靠感觉

| 指标 | 含义 | 衡量 |
| --- | --- | --- |
| 命中率 / Recall@K | 相关片段是否被召回 | 检索质量 |
| 忠实度（Faithfulness） | 答案是否都来自材料 | 幻觉程度 |
| 相关性（Relevance） | 答案是否回答了问题 | 生成质量 |

评估做法：手工标注黄金问答集 + 用强模型（GPT-4）当裁判打分，每次改动切块/检索策略都跑一遍回归。

## 11.7 实战：本地文档问答助手（完整可运行）

把 11.5 升级为**带 Chroma 持久化 + 元数据 + 对话**的完整工具。文档更新后重跑 `index()` 即可重新入库。

```python
# 3_rag_assistant.py —— 本地文档问答助手（Chroma 版）
import glob
from pathlib import Path
import chromadb
from openai import OpenAI

client = OpenAI()
DB_PATH = "./chroma_db"

# ── 1. 切块（段落级，保留来源元数据）──
def load_chunks(doc_dir: str) -> list[dict]:
    chunks = []
    for file in glob.glob(str(Path(doc_dir) / "*.md")):
        text = Path(file).read_text(encoding="utf-8")
        for i, para in enumerate(text.split("\n\n")):
            para = para.strip()
            if len(para) >= 20:                       # 过滤过短片段
                chunks.append({"id": f"{Path(file).name}:{i}",
                               "text": para,
                               "meta": {"source": Path(file).name}})
    return chunks

# ── 2. 索引：写入 Chroma（自动调用默认 Embedding，也可换 OpenAI）──
def index(doc_dir: str):
    chroma = chromadb.PersistentClient(path=DB_PATH)   # 本地持久化
    col = chroma.get_or_create_collection("kb")
    chunks = load_chunks(doc_dir)
    col.upsert(ids=[c["id"] for c in chunks],
               documents=[c["text"] for c in chunks],
               metadatas=[c["meta"] for c in chunks])
    print(f"索引完成：{len(chunks)} 个片段")

# ── 3. 检索 + 生成（带来源引用）──
def ask(question: str, n: int = 3) -> str:
    chroma = chromadb.PersistentClient(path=DB_PATH)
    col = chroma.get_collection("kb")
    hits = col.query(query_texts=[question], n_results=n)   # 向量检索

    docs = hits["documents"][0]
    metas = hits["metadatas"][0]
    context = "\n".join(f"- {d}" for d in docs)
    sources = ", ".join(m["source"] for m in metas)

    prompt = f"""只依据【知识片段】回答，不得编造。回答末尾标注引用来源。
【知识片段】
{context}
【问题】{question}"""

    resp = client.chat.completions.create(
        model="gpt-4o-mini", messages=[{"role": "user", "content": prompt}]
    )
    return f"{resp.choices[0].message.content}\n\n（引用：{sources}）"

if __name__ == "__main__":
    # 准备演示文档
    demo = Path("docs_kb"); demo.mkdir(exist_ok=True)
    (demo / "客服政策.md").write_text(
        "退款条件：商品已签收且金额不超过 200 元可申请退款。\n\n"
        "发货时效：现货订单 24 小时内发货。\n\n"
        "优惠券：满 199 减 30，每个订单限用一张。\n\n"
        "会员等级：年消费满 5000 元升级为黄金会员，享 9 折。",
        encoding="utf-8",
    )
    (demo / "产品说明.md").write_text(
        "智能音箱支持语音点歌、定闹钟、控制智能家居。\n\n"
        "产品保修期一年，人为损坏不在保修范围。",
        encoding="utf-8",
    )

    index("docs_kb")
    while True:
        q = input("问题（quit 退出）：")
        if q.lower() == "quit":
            break
        print("回答：", ask(q), "\n")
```

**这个实战演示了 RAG 的完整闭环**：文档切块 → 元数据 → Chroma 持久化索引 → 语义检索 → 带引用生成。把 `chromadb` 换成 pgvector/Qdrant 只需改几行客户端代码，架构不变。

## 11.8 练习与面试

### 本章练习

1. 用 11.3 的余弦相似度验证三句话的语义远近，并解释为什么"手机"与"智能手机"距离近；
2. 给 11.5 手写 RAG 增加 `k=3` 与"最低相似度阈值"（低于 0.3 直接答"资料中没有"）；
3. 把 11.7 的文档换成你自己的一批笔记/资料（.md），跑通问答并验证引用来源正确；
4. 实现元数据过滤：让 Chroma 只检索指定来源文件的片段（`where={"source": ...}`）；
5. 实现简单混合检索：向量 + 关键字（订单号精确匹配）两路合并；
6. 对比不同切块大小（200/800 token）对同一问题的回答质量差异；
7. 把 RAG 助手封装成 FastAPI 接口（第 6 章），让 Web 端可调用；
8. 选做：接入 Rerank（用 `text-embedding` 双编码近似或 cross-encoder 库），对比重排前后的 Top-5 质量。

### 面试题

| 问题 | 要点 |
| --- | --- |
| RAG 是什么？解决了什么问题？ | 检索增强生成；时效性/私有数据/幻觉三大缺陷 |
| RAG 和微调的区别？ | 微调改权重（贵、慢、风格），RAG 按需取数（实时、可溯源、便宜） |
| Embedding 是什么？ | 文字→向量，语义相近则向量距离近 |
| 向量检索的原理？ | 余弦/欧氏距离找最近邻；大规模用 ANN 索引（HNSW 等） |
| 为什么检索结果要切块？ | 整篇向量化语义稀释；块太小丢上下文；常用 500~1000 token |
| 切块的注意事项？ | 保留元数据（来源/页码）用于过滤与溯源；重叠避免截断语义 |
| 向量检索的缺点？ | 不擅长精确匹配（订单号）；需要混合检索兜底 |
| 什么是混合检索？ | BM25 关键字 + 向量两路召回再合并 |
| 什么是 Rerank？ | 粗召回后逐对打分重排，提升 Top-K 精度 |
| 向量数据库怎么选？ | 学习 Chroma、已有 PG 用 pgvector、已有 ES 用 dense_vector、大流量 Qdrant/Milvus |
| RAG 幻觉怎么缓解？ | 检索质量 + Prompt 约束"只依据材料"+ 阈值拒绝 + 忠实度评估 |
| RAG 怎么评估？ | Recall@K、忠实度、相关性；强模型当裁判打分 |
| RAG 和 Agent 什么关系？ | RAG 是知识增强能力，可作为一个工具/检索节点集成进 Agent |
| 长上下文能替代 RAG 吗？ | 小文档可以；大知识库 token 贵、注意力下降，仍需 RAG |

### 本章小结

- **RAG 原理**（11.1~11.2）：知识三缺陷、三段式流水线（索引/检索/生成）、检索是质量天花板；
- **Embedding**（11.3）：语义坐标、余弦相似度、OpenAI Embedding API；
- **向量库**（11.4）：Chroma 起步、pgvector/Qdrant 演进、对标 ES；
- **手写最小 RAG**（11.5）：20 行理解检索+生成全流程；
- **生产要点**（11.6）：切块、元数据过滤、混合检索 + Rerank、四类评估指标；
- **实战**（11.7）：本地文档问答助手——切块入库、持久化、带引用回答。

---

## 🚀 后续进阶方向

- **GraphRAG**：把知识抽成图谱再检索，擅长多跳关系问答；
- **重排与精排**：cross-encoder 深度调优、多路召回融合；
- **长文档**：Map-Reduce / 递归摘要式问答；
- **多模态 RAG**：图片（CLIP 向量）、表格、PDF 版式解析。

配套：下一章 [12-LangGraph编排.md](./12-LangGraph编排.md)（把 RAG 作为节点编排进 Agent 工作流）｜ [README.md](./README.md)
