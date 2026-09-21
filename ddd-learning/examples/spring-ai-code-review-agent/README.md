# Spring AI Code Review Agent

> 基于 Spring AI 1.x 的多 Agent 协作自动化 Code Review 系统。
> 对应教程章节：[第八十五章](../../java-learning/85-SpringAI搭建内部CodeReviewAgent.md)

## 1. 项目简介

本项目是一个**企业内可私有部署**的自动化 Code Review Agent 服务，特点：

- **多 Agent 协作**：5 个专项 Agent 并行审查（安全/性能/DDD/测试/风格）
- **多 LLM 切换**：OpenAI / 通义千问 / DeepSeek / Ollama 一键切换
- **多平台支持**：GitLab MR + GitHub PR 同时接入
- **完整可运行**：Spring Boot 3.3 + Spring AI 1.x + Docker Compose

## 2. 工程结构

```
spring-ai-code-review-agent/
├── pom.xml                   ← 父工程
├── docker-compose.yml        ← 一键启动
├── monitoring/               ← Prometheus 配置（可选）
├── review-core/              ← 核心抽象层
├── review-subagents/         ← 5 个专项 SubAgent
├── review-server/            ← 服务端（Orchestrator + Webhook）
└── README.md
```

## 3. 快速启动

### 3.1 前置环境

- JDK 17+
- Maven 3.9+
- Docker & Docker Compose（推荐）
- LLM API Key（OpenAI / 阿里云 / DeepSeek 任选）

### 3.2 启动步骤

```bash
# 1. 克隆/下载本工程
cd spring-ai-code-review-agent

# 2. 配置环境变量
export OPENAI_API_KEY=sk-xxxxx              # OpenAI Key
# 或
export DASHSCOPE_API_KEY=sk-xxxxx           # 阿里云
# 或
export DEEPSEEK_API_KEY=sk-xxxxx            # DeepSeek
export GITLAB_URL=https://gitlab.example.com
export GITLAB_TOKEN=glpat-xxxxx

# 3. 一键启动
docker-compose up -d

# 4. 验证
curl http://localhost:8080/actuator/health
# → {"status":"UP"}

# 5. 配置 GitLab Webhook
# 项目 → Settings → Webhooks
#   URL: http://your-host:8080/webhook/gitlab
#   Trigger: Merge request events
#   Secret Token: <your-token>
```

### 3.3 本地开发（无 Docker）

```bash
# 启动依赖
docker-compose up -d postgres redis

# 启动服务
cd review-server
SPRING_PROFILES_ACTIVE=openai \
OPENAI_API_KEY=sk-xxxxx \
GITLAB_TOKEN=glpat-xxxxx \
mvn spring-boot:run
```

## 4. LLM Provider 切换

通过环境变量切换 LLM，无需改代码：

```bash
# OpenAI
SPRING_PROFILES_ACTIVE=openai OPENAI_API_KEY=sk-xxx

# 阿里云 DashScope
SPRING_PROFILES_ACTIVE=dashscope DASHSCOPE_API_KEY=sk-xxx

# DeepSeek
SPRING_PROFILES_ACTIVE=deepseek DEEPSEEK_API_KEY=sk-xxx

# Ollama（本地）
SPRING_PROFILES_ACTIVE=ollama
# 默认连接 http://localhost:11434，需提前 ollama pull qwen2.5-coder:7b
```

## 5. Webhook 配置示例

### 5.1 GitLab

```
URL:    http://<host>:8080/webhook/gitlab
Token:  <your-secret>
Trigger: ☑ Merge request events
```

### 5.2 GitHub

```
Payload URL: http://<host>:8080/webhook/github
Content type: application/json
Events: ☑ Pull requests
```

## 6. 自定义 Agent

```java
// 1. 新建一个类继承 CodeReviewAgent
@Component
public class MyCustomAgent extends CodeReviewAgent {
    @Override public String name() { return "MyCustom"; }
    @Override public String systemPrompt() { return "你是 MyCustom 审查专家..."; }
}

// 2. 完成。Spring 自动扫描并加入并行调度。
```

## 7. 关键配置（`application.yml`）

```yaml
review:
  enabled-agents:           # 启用的 Agent
    - Security
    - Performance
    - DDD
    - TestCoverage
    - Style
  timeout-seconds: 120      # 单 Agent 超时
  diff-truncate-bytes: 50000# 大 diff 截断阈值
  block-on:                 # 触发阻塞合并的严重度
    - BLOCKER
    - CRITICAL
```

## 8. 验证

### 8.1 模拟 GitLab Webhook

```bash
curl -X POST http://localhost:8080/webhook/gitlab \
  -H "Content-Type: application/json" \
  -H "X-Gitlab-Token: test-token" \
  -d @sample-webhook.json
```

### 8.2 查看审查报告

去 GitLab/GitHub PR 页面，应该看到：
- 顶层评论（带统计表格）
- 每条意见的 inline 评论
- Commit Status 状态（成功/失败）

## 9. 与教程的对应

| 教程小节 | 代码文件 |
|---|---|
| 5.2 DTO | `review-core/.../model/*.java` |
| 5.2.5 Agent 基类 | `review-core/.../agent/CodeReviewAgent.java` |
| 5.2.6 工具集 | `review-core/.../tool/GitTools.java` |
| 6.1~6.5 5 个 SubAgent | `review-subagents/.../agents/*.java` |
| 7.1 Orchestrator | `review-server/.../orchestrator/ReviewOrchestrator.java` |
| 7.2 Aggregator | `review-server/.../orchestrator/ReviewAggregator.java` |
| 8.2 GitLab | `review-server/.../platform/GitLabPlatformClient.java` |
| 8.3 GitHub | `review-server/.../platform/GitHubPlatformClient.java` |
| 9.1 Webhook | `review-server/.../controller/ReviewWebhookController.java` |
| 11 测试 | `review-server/src/test/...` |

## 10. 路线图

- [ ] 增量审查（只看 diff 不看全文件）
- [ ] 本地规则引擎先筛（Checkstyle/PMD 违规行号喂给 LLM）
- [ ] 学习反馈（用户标记误报 → 自动降权）
- [ ] 多语言支持（Python/Go/TS）
- [ ] 自动修复（生成 fix commit）
- [ ] Slack/Teams 集成

## 11. License

MIT