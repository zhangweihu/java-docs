# 第八十四章 AI Agent Harness 实战：LLM Tool Calling + ReAct + Spring AI + Agent 可观测

> **章节定位**：Java 工具库扩展专题（AI 前沿）。**AI Agent Harness** 指"为 LLM 提供可控运行环境 + 工具注册 + 记忆管理 + 计划编排 + 可观测"的整套基础设施。2024-2026 年 OpenAI GPT-4o、Anthropic Claude 3.5+、Google Gemini 等模型的 Function Calling 能力成熟，Agent Harness 从研究范式走向工程标准。
>
> **学习目标**：理解 Agent Harness 的核心组件（LLM + Tool + Memory + Planner + Executor + Evaluator），能用 Spring AI 1.x 在 Java 工程里搭建多工具 Agent，能用 Function Calling 实战对话，能讲清 ReAct、Plan-and-Execute、Reflection 三种范式。
>
> **前置知识**：Java 17、Spring Boot 3.2+、OpenAI API 基础、Prompt 工程基础。

---

## 一、为什么需要 AI Agent Harness

LLM 直接调用有 4 个典型问题：

1. **知识截止**：LLM 不知道"今天北京天气"、"我的订单状态"，需要外部工具实时拉取；
2. **无法执行动作**：LLM 只能生成文字，不能"下单""转账""发邮件"，需要工具调用；
3. **无状态**：多轮对话需要历史记忆管理；
4. **不可观测**：LLM 是黑盒，调试难、评估难、合规难。

Agent Harness 的价值：**让 LLM 具备"感知 → 思考 → 行动 → 反思"完整能力**，并把整个过程结构化、可观测、可治理。

---

## 二、Agent Harness 核心组件

```
AI Agent Harness
├── LLM             大脑（GPT-4o / Claude 3.5 / Gemini / Qwen）
├── Tool Registry   工具仓库（Function Calling 列表）
├── Memory          记忆（短期对话 + 长期向量库）
├── Planner         计划器（决定下一步做什么）
├── Executor        执行器（调用 Tool / API）
├── Evaluator       评估器（Reflection / 评分）
└── Tracer          追踪器（LangSmith / LangFuse）
```

记忆口诀：**LLM 想、Tool 做、Memory 记、Planner 排、Executor 跑、Evaluator 评、Tracer 看**。

---

## 三、5 款 Agent Harness 对比矩阵

| 框架 | 语言 | 优势 | 劣势 | 适用场景 |
|---|---|---|---|---|
| **LangChain / LangGraph** | Python + TS | 生态最大、文档最全、工具链丰富 | 抽象层厚、版本迭代快 | **Python 主流** |
| **OpenAI Assistants API / Agents SDK** | Python | OpenAI 一手集成、托管 Runtimes 内存 | 仅 GPT 系模型 | 单一 OpenAI 场景 |
| **Spring AI** | Java | Spring 体系一手集成、Java 工程师友好 | 起步晚（2024）、生态小于 LangChain | **本主角，Java 路线** |
| **Anthropic SDK + Tool Use** | Python + TS | Claude 一手集成、长上下文 | 仅 Claude | Claude 系场景 |
| **Microsoft AutoGen** | Python | 多 Agent 编排强大 | 仅 Python | 复杂多 Agent 协作 |

**选择建议**：
- Java 后端 → **Spring AI**（本章主角）+ Spring Boot
- Python/数据科学 → **LangGraph**（生产级） / **OpenAI Agents SDK**（轻量）
- 多 Agent 复杂协作 → **AutoGen**
- 单一 Claude 模型 → Anthropic SDK

---

## 四、环境与依赖（Spring AI）

```1:a:java-learning/84-AIAgentHarness实战.md
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    <version>1.0.0-M6</version>
</dependency>

<!-- 可选：Anthropic / Gemini / Ollama -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-anthropic-spring-boot-starter</artifactId>
    <version>1.0.0-M6</version>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-ollama-spring-boot-starter</artifactId>
    <version>1.0.0-M6</version>
</dependency>
```

application.yml：
```1:a:java-learning/84-AIAgentHarness实战.md
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o
          temperature: 0.7
```

---

## 五、LLM 基础：ChatClient

### 5.1 单轮对话

```1:a:java-learning/84-AIAgentHarness实战.md
@RestController
@RequiredArgsConstructor
class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String prompt) {
        return chatClient.prompt(prompt).call().content();
    }
}
```

### 5.2 多轮对话（内存）

```1:a:java-learning/84-AIAgentHarness实战实战.md
@GetMapping("/chat/conv")
public String conv(@RequestParam String userId, @RequestParam String message) {
    return chatClient.prompt()
        .user(message)
        .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
        .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, userId))
        .call()
        .content();
}
```

---

## 六、Tool Calling 实战（Function Calling）

### 6.1 工具定义

```1:a:java-learning/84-AIAgentHarness实战.md
@Service
@RequiredArgsConstructor
public class WeatherTools {

    @Tool(description = "根据城市名查询实时天气")
    public WeatherInfo getWeather(
        @ToolParam(description = "城市名称，如'北京'") String city) {
        return weatherService.fetch(city);
    }

    @Tool(description = "查询订单状态")
    public OrderStatus getOrderStatus(
        @ToolParam(description = "订单 ID") Long orderId) {
        return orderRepository.findById(orderId)
            .map(o -> new OrderStatus(o.getStatus(), o.getTotal()))
            .orElseThrow();
    }
}

record WeatherInfo(String city, double temp, String desc) {}
```

### 6.2 Agent 创建

```1:a:java-learning/84-AIAgentHarness实战.md
@Configuration
class AgentConfig {

    @Bean
    ChatClient weatherAgent(ChatClient.Builder builder, WeatherTools tools) {
        return builder
            .defaultSystem("你是天气助手。用户询问天气时调用 get_weather 工具。")
            .defaultTools(tools)
            .build();
    }
}
```

### 6.3 调用

```1:a:java-learning/84-AIAgentHarness实战.md
@GetMapping("/ask")
public String ask(@RequestParam String question) {
    return weatherAgent.prompt()
        .user(question)
        .call()
        .content();
}

// 调用示例
// GET /ask?question=北京今天天气如何？
// → Agent 自动调用 getWeather("北京")，返回："北京今天晴，25℃"
```

---

## 七、ReAct 模式实战

### 7.1 ReAct = Reasoning + Acting

LLM 在每轮推理时输出"思考 + 行动 + 观察"，再循环。Spring AI 默认开启 ReAct。

```
Q: 我的订单 #12345 状态如何？
Thought: 用户问订单状态，我应该调用 get_order_status 工具
Action: get_order_status(order_id=12345)
Observation: 订单状态：已发货，金额 ¥299
Thought: 我已经获取到订单信息
Final Answer: 您的订单 #12345 已发货，金额 ¥299
```

### 7.2 自定义 ReAct 提示词

```1:a:java-learning/84-AIAgentHarness实战实战.md
String reactPrompt = """
    Answer the following question using ReAct pattern:
    
    Thought: 思考下一步要做什么
    Action: 调用哪个工具
    Action Input: 工具参数
    Observation: 工具返回结果
    ... (重复)
    Final Answer: 最终答案
    """;

String answer = chatClient.prompt()
    .system(reactPrompt)
    .user(question)
    .call()
    .content();
```

---

## 八、Memory（记忆）实战

### 8.1 短期对话记忆

```1:a:java-learning/84-AIAgentHarness实战实战.md
@Bean
ChatMemory chatMemory() {
    return new InMemoryChatMemory();  // 进程内，重启清空
}
```

### 8.2 长期向量记忆（PG + pgvector）

```1:a:java-learning/84-AIAgentHarness实战实战.md
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-pgvector-store-spring-boot-starter</artifactId>
</dependency>

@Bean
VectorStore vectorStore(JdbcTemplate jdbcTemplate,
                        EmbeddingClient embeddingClient) {
    return new PgVectorStore(jdbcTemplate, embeddingClient);
}

@Bean
ChatMemory longTermMemory(VectorStore vectorStore, EmbeddingClient embeddingClient) {
    return new VectorStoreChatMemory(vectorStore, embeddingClient);
}
```

### 8.3 用户偏好记忆

```1:a:java-learning/84-AIAgentHarness实战实战.md
@Service
@RequiredArgsConstructor
class PreferenceAgent {

    private final VectorStore vectorStore;
    private final EmbeddingClient embeddingClient;
    private final ChatClient chatClient;

    public String chat(String userId, String message) {
        // 1. 检索历史偏好
        List<Document> prefs = vectorStore.similaritySearch(
            SearchRequest.query(message)
                .withFilterExpression("userId == '" + userId + "'")
                .withTopK(3)
        );

        // 2. 拼接上下文
        String context = prefs.stream()
            .map(Document::getContent)
            .collect(Collectors.joining("\n"));

        // 3. 调用 LLM
        return chatClient.prompt()
            .system("""
                你是一个个性化助手。
                用户偏好：%s
                """.formatted(context))
            .user(message)
            .call()
            .content();
    }
}
```

---

## 九、Plan-and-Execute 模式

### 9.1 ReAct vs Plan-and-Execute

| 模式 | 思路 | 适用 |
|---|---|---|
| ReAct | 边想边做，每步推理 | 简单任务、对话场景 |
| Plan-and-Execute | 先列计划，再串行执行 | 复杂任务、多步骤 |

### 9.2 Spring AI 实现（自定义）

```1:a:java-learning/84-AIAgentHarness实战实战.md
@Service
@RequiredArgsConstructor
class PlanExecuteAgent {

    private final ChatClient plannerClient;
    private final ChatClient executorClient;

    public String execute(String task) {
        // 1. 规划步骤
        String plan = plannerClient.prompt()
            .system("""
                你是一个任务规划助手。
                把复杂任务拆成有序步骤，每行一个：
                1. xxx
                2. xxx
                """)
            .user(task)
            .call()
            .content();

        List<String> steps = plan.lines().toList();

        // 2. 顺序执行
        StringBuilder result = new StringBuilder();
        for (String step : steps) {
            String stepResult = executorClient.prompt()
                .system("执行：'%s'，当前状态：'%s'".formatted(step, result))
                .user(step)
                .tools(weatherTools, orderTools)
                .call()
                .content();
            result.append("Step: ").append(step).append(" → ").append(stepResult).append("\n");
        }
        return result.toString();
    }
}
```

---

## 十、多 Agent 协作

### 10.1 编排器 + 工作者模式

```
       ┌────────────────┐
       │ Orchestrator   │  ← 主调度
       └────────┬───────┘
                │
    ┌───────────┼───────────┐
    ▼           ▼           ▼
┌────────┐ ┌────────┐ ┌────────┐
│Research│ │ Writer │ │Reviewer│  ← 专家 Agent
│ Agent  │ │ Agent  │ │ Agent  │
└────────┘ └────────┘ └────────┘
```

### 10.2 Spring AI 实现

```1:a:java-learning/84-AIAgentHarness实战实战.md
@Service
@RequiredArgsConstructor
class WriterPipeline {

    private final ChatClient researchAgent;
    private final ChatClient writerAgent;
    private final ChatClient reviewerAgent;

    public Article write(String topic) {
        // 1. 研究阶段
        String research = researchAgent.prompt()
            .user("调研主题：%s，输出 5 个关键点".formatted(topic))
            .call()
            .content();

        // 2. 撰写阶段
        String draft = writerAgent.prompt()
            .user("""
                主题：%s
                研究要点：%s
                输出 500 字短文。
                """.formatted(topic, research))
            .call()
            .content();

        // 3. 审校阶段
        String final_ = reviewerAgent.prompt()
            .user("""
                请审校以下文章，给出改进建议：
                %s
                """.formatted(draft))
            .call()
            .content();

        return new Article(draft, final_);
    }
}
```

---

## 十一、Agent 可观测与评估

### 11.1 Spring AI Tracing

```1:a:java-learning/84-AIAgentHarness实战实战.md
@Configuration
class ObservabilityConfig {

    @Bean
    ObservationRegistry observationRegistry() {
        return ObservationRegistry.create();
    }
}
```

Spring Boot Actuator 暴露：
```
GET /actuator/metrics/spring.ai.chat.client
GET /actuator/metrics/spring.ai.chat.tokens
GET /actuator/metrics/spring.ai.tool.calls
```

### 11.2 LangFuse / LangSmith 集成（Python 端，Java 端用 HTTP）

```1:a:java-learning/84-AIAgentHarness实战实战.md
@Bean
ChatClient tracedClient(ChatClient.Builder builder,
                        @Value("${langfuse.url}") String langfuseUrl,
                        @Value("${langfuse.key}") String key) {
    return builder
        .defaultAdvisors(new SimpleLoggerAdvisor())
        .build();
}

// 自定义 Advisor 把每轮对话上报 LangFuse
public class LangfuseAdvisor implements RequestResponseAdvisor {
    @Override
    public AdvisedResponse aroundCall(AdvisedRequest req, CallChain chain) {
        AdvisedResponse resp = chain.nextCall(req);
        // POST 到 LangFuse
        langfuseClient.trace(req, resp);
        return resp;
    }
}
```

### 11.3 Token 成本控制

```1:a:java-learning/84-AIAgentHarness实战实战.md
@Bean
ChatClient costAwareClient(ChatClient.Builder builder,
                           MeterRegistry meterRegistry) {
    return builder
        .defaultAdvisors(new TokenUsageAdvisor(meterRegistry))
        .build();
}
```

Grafana 看板可监控：
- 每分钟 token 用量
- 每个用户成本
- 每个工具调用频次
- 每个 Agent 的平均轮次

---

## 十二、企业实战：客服 Agent

### 12.1 业务场景

客服 Agent 自动回答"订单状态""退款进度""物流查询"，无法回答转人工。

### 12.2 工具集

```1:a:java-learning/84-AIAgentHarness实战实战.md
@Service
@RequiredArgsConstructor
class CustomerServiceTools {

    @Tool(description = "查询订单状态和物流")
    public OrderInfo getOrder(@ToolParam(description = "订单号") String orderNo) {
        return orderService.getByOrderNo(orderNo);
    }

    @Tool(description = "申请退款")
    public RefundResult applyRefund(
        @ToolParam(description = "订单号") String orderNo,
        @ToolParam(description = "退款原因") String reason) {
        return refundService.apply(orderNo, reason);
    }

    @Tool(description = "转接人工客服")
    public String transferToHuman(@ToolParam(description = "用户问题") String issue) {
        return "已为您转接人工客服，预计等待 2 分钟";
    }
}
```

### 12.3 Agent 配置

```1:a:java-learning/84-AIAgentHarness实战实战.md
@Configuration
class CustomerAgentConfig {

    @Bean
    ChatClient customerAgent(ChatClient.Builder builder,
                             CustomerServiceTools tools) {
        return builder
            .defaultSystem("""
                你是 XX 电商客服 Agent。
                - 订单查询、退款进度调用对应工具；
                - 用户投诉、转人工客服场景调用 transfer_to_human；
                - 不确定时回答'我帮您转接人工'；
                - 不要编造订单信息。
                """)
            .defaultTools(tools)
            .build();
    }
}
```

### 12.4 Controller

```1:a:java-learning/84-AIAgentHarness实战实战.md
@RestController
@RequiredArgsConstructor
class CustomerApiController {

    private final ChatClient customerAgent;

    @PostMapping("/api/support/chat")
    public String chat(@RequestBody ChatRequest req) {
        return customerAgent.prompt()
            .user(req.message())
            .advisors(a -> a.param("conversation_id", req.userId()))
            .call()
            .content();
    }
}

record ChatRequest(String userId, String message) {}
```

---

## 十三、踩坑与最佳实践

### 13.1 十大经典坑

1. **幻觉（Hallucination）**
   - LLM 编造订单号、地址；解决：强约束"不确定时调用工具"，不允许 LLM 编造事实。

2. **Token 成本爆炸**
   - 多轮对话把全历史塞进 context，单次调用 10 万 token；解决：`VectorStoreChatMemory` 只检索相关片段。

3. **Tool Calling 死循环**
   - LLM 反复调用同一个工具；解决：设置 `max_iterations=10` 上限。

4. **超时控制缺失**
   - LLM 调用 60s 没响应；解决：设置 timeout + 异步队列（RabbitMQ / RocketMQ）。

5. **敏感数据泄漏**
   - 用户手机号、身份证被拼进 prompt；解决：PII 脱敏 + 审计日志。

6. **多 Agent 通信开销**
   - 3 个 Agent 各调一次 LLM，单请求 4x 成本；解决：合并 Agent + 共享 context。

7. **可观测缺失**
   - 线上 Agent 失败排查不到；解决：LangSmith / LangFuse 追踪每轮 Tool 调用。

8. **模型版本管理**
   - 升级 GPT-4o → GPT-5 后线上行为变化；解决：提示词版本管理 + A/B 测试。

9. **Function Calling 参数错误**
   - LLM 传错参数类型（如 `orderId` 传成字符串）；解决：Schema 强校验 + 重试机制。

10. **冷启动延迟**
    - 首次调用 LLM 慢（10s）；解决：连接池 + 预热 + 流式响应（SSE）。

### 13.2 性能与成本优化

| 场景 | 优化手段 |
|---|---|
| 长对话 | Vector Memory 检索而非全量拼接 |
| 高并发 | 异步流式（SSE）+ Tomcat maxThreads |
| 成本控制 | 小模型（gpt-4o-mini）+ 缓存常见问题 |
| 准确度 | Function Calling 强约束 + Reflection |
| 离线评测 | LangSmith 数据集 + A/B 测试 |
| 合规审计 | 完整 trace + 日志脱敏 + Prompt 版本管理 |

---

## 十四、面试常问 5 题

**Q1：Agent 与 LLM 调用的核心区别？**
A：LLM 只能生成文本；Agent 在 LLM 基础上**调用工具**（Function Calling）+ **记忆**（Memory）+ **计划**（Planning）+ **反思**（Reflection），能完成"查询订单→退款→发通知"等多步骤真实业务。

**Q2：ReAct vs Plan-and-Execute 的选型？**
A：
- **ReAct**：每步推理决定下一步行动，适合对话、简单任务；
- **Plan-and-Execute**：先列计划再执行，适合复杂任务（如"调研→撰写→审校"三步）。
生产建议：简单任务用 ReAct（Spring AI 内置），复杂多步用 Plan-and-Execute（自实现 Planner）。

**Q3：Function Calling 与 JSON Mode 的区别？**
A：
- **Function Calling**：模型返回结构化"工具调用"指令，由 Harness 执行；
- **JSON Mode**：模型直接返回 JSON 字符串，应用层解析。
**Function Calling 更可靠**：模型被强约束调用哪个函数、参数是什么 Schema。

**Q4：Agent Harness 与 LangChain 的关系？**
A：**LangChain 是 Python 生态的 Agent Harness**，包含 Tools / Memory / Chains / Agents / Callbacks 等。Spring AI 是 Java 生态的对应实现，接口设计与 LangChain 对齐，但绑定 Spring 生态。

**Q5：Agent 失败的兜底策略？**
A：三层兜底：
1. **解析失败**：Tool 返回值无法解析 → 重试 3 次 → 仍失败转人工；
2. **超时**：单次 Tool 调用 > 30s → 中止 + 转人工；
3. **幻觉检测**：Tool 返回结果与 LLM 回答不符 → Reflection 提示 LLM 重新核对。

---

## 十五、与本仓库其他章节的衔接

| 主线章节 | 本章关联 |
|---|---|
| 第 16 章 Spring Boot 实战 | Spring AI 集成 Spring Boot |
| 第 36 章 搜索引擎 | VectorStore（向量检索）= PG + pgvector |
| 第 48 章 事件溯源 | Agent 状态机（类似 Event Sourcing 的事件流） |
| 第 71-80 章 DDD | Agent 设计借鉴限界上下文（每个 Agent = 一个 BC） |
| 第 83 章 测试 Harness | Agent 评估（LangSmith 数据集）类似 JaCoCo 覆盖率 |

---

## 十六、收官总结

AI Agent Harness 是 2024-2026 年 LLM 应用工程化的核心范式。本章覆盖了：

- 5 大核心组件：LLM / Tool / Memory / Planner / Executor / Tracer
- 5 款主流框架：LangChain / OpenAI Agents / Spring AI / Anthropic / AutoGen
- 实战：ChatClient + Tool Calling + ReAct + Memory + Plan-and-Execute + 多 Agent
- 可观测：Spring AI Tracing + LangFuse
- 企业实战：客服 Agent 完整链路
- 10 大踩坑：幻觉、成本爆炸、Tool 死循环、超时、敏感泄漏等
- 5 道面试题

掌握本章后，能独立设计中型 Agent 系统（电商客服、数据分析、文档问答），并具备向 LangGraph / AutoGen 等 Python 生态迁移的能力。