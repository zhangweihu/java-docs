# 第八十五章 Spring AI 搭建内部 Code Review Agent

> 本章是"AI Agent Harness"在企业 DevOps 场景下的落地终极篇。
> 目标：用 Spring AI 1.x + 多 Agent 协作 + GitLab/GitHub API，搭一个**真正能跑在生产环境**的自动化 Code Review 服务。
> 配套示例工程：`ddd-learning/examples/spring-ai-code-review-agent/`

---

## 1. 痛点与目标

### 1.1 现状痛点

| 痛点 | 数据（行业基准） |
| --- | --- |
| 人工 Review 平均耗时 | **30 ~ 60 分钟 / PR**（Senior 资源稀缺） |
| 评审延迟 | **2 ~ 8 小时**（等齐 Reviewer） |
| 规范不一致 | 同一团队 5 个人有 5 套标准 |
| 重复问题高频 | NPE、SQL 注入、空指针 反复出现 |
| Reviewer 抱怨 | "又是空指针，注释不全，没有测试" |

### 1.2 目标与边界

```
能做：
✅ 自动发现空指针 / SQL 注入 / 资源未关闭 / 性能退化 / 风格违规
✅ 自动审查 DDD 聚合边界 / 事务边界 / 单元测试覆盖度
✅ 把评审意见直接回写到 PR / MR 评论
✅ 严重问题 → 阻塞合并 + 飞书/钉钉/邮件通知

不做：
❌ 业务逻辑正确性（领域模型需人工 Review）
❌ 业务规则正确性（业务 Analyst 职责）
❌ 架构调整建议（架构师职责）
```

**重要原则**：把"低级、重复、机械"问题全部自动化，让 Senior Reviewer **只关注业务和架构**。

---

## 2. 架构总览

### 2.1 多 Agent 协作全景图

```
                        ┌───────────────────────┐
   GitLab/GitHub        │   review-server       │       GitLab/GitHub
   Webhook   ───────────►  Controller          ────────►   PR/MR Comments
                        │       │              │
                        │       ▼              │
                        │  ReviewOrchestrator   │
                        │       │              │
                        │       ▼  并行调度    │
                        │  ┌───────────────┐   │
                        │  │ SubAgent × 5  │   │
                        │  ├───────────────┤   │
                        │  │ 1.安全审查     │   │
                        │  │ 2.性能审查     │   │
                        │  │ 3.DDD 规范审查│   │
                        │  │ 4.测试覆盖审查│   │
                        │  │ 5.代码风格审查│   │
                        │  └───────────────┘   │
                        │       │              │
                        │       ▼              │
                        │  ReviewAggregator     │
                        │  (严重度评级/合并去重)│
                        │       │              │
                        │       ▼              │
                        │  Platform Client      │
                        └───────────────┬───────┘
                                        │
                                        ▼
                              Redis(去重) + PostgreSQL(规则)
```

### 2.2 5 个 SubAgent 的职责矩阵

| SubAgent | 职责 | 核心检查项 | 工具集 |
|---|---|---|---|
| **SecurityAgent** | 安全审查 | SQL 注入、XSS、CSRF、密钥硬编码、未授权访问、敏感日志 | git_diff / grep / read_file |
| **PerformanceAgent** | 性能审查 | N+1 查询、慢 SQL、内存泄漏、循环内 IO、不必要的序列化 | git_diff / grep / sonar_api |
| **DddAgent** | 架构/DDD 审查 | 贫血/充血、聚合根封装、事务边界、领域服务拆分、ACL | git_diff / grep / read_file |
| **TestCoverageAgent** | 测试审查 | 单测覆盖度、断言质量、边界用例、Mockito 使用 | git_diff / grep / read_file |
| **StyleAgent** | 风格审查 | 命名规范、注释完整度、Checkstyle 规则、Lombok 滥用 | git_diff / grep / read_file |

---

## 3. 技术栈

| 组件 | 选型 | 版本 |
|---|---|---|
| 应用框架 | Spring Boot | 3.3.x |
| AI 框架 | Spring AI | 1.0.x |
| LLM Provider | OpenAI / 阿里云 DashScope / DeepSeek / Ollama | 可热切换 |
| GitLab 客户端 | gitlab4j-api | 25.x |
| GitHub 客户端 | org.kohsuke:github-api | 1.318+ |
| 异步 | `@Async` + `ThreadPoolTaskExecutor` | JDK 17 |
| 缓存 | Redisson（去重 + 限流） | 3.27+ |
| 规则存储 | PostgreSQL / H2（测试） | 15+ |
| 消息通知 | 飞书 / 钉钉 Webhook | - |

---

## 4. Maven 多模块工程结构

```
spring-ai-code-review-agent/
├── pom.xml                 ← 父工程（dependencyManagement）
├── docker-compose.yml      ← Redis + Postgres + Agent
├── README.md
├── review-core/            ← 核心抽象（基类 + 工具 + DTO）
│   └── src/main/java/com/example/review/core/
│       ├── agent/
│       │   ├── CodeReviewAgent.java   ← 抽象基类
│       │   └── ReviewSubAgent.java    ← 子 Agent 接口
│       ├── tool/
│       │   ├── GitTools.java
│       │   ├── FileTools.java
│       │   └── GrepTools.java
│       ├── model/
│       │   ├── ReviewComment.java     ← 评论 DTO
│       │   ├── ReviewSeverity.java    ← 严重度枚举
│       │   ├── ReviewReport.java      ← 完整报告
│       │   └── PullRequestContext.java← PR 上下文
│       └── exception/
│           └── ReviewException.java
│
├── review-subagents/       ← 5 个专项 SubAgent 实现
│   └── src/main/java/com/example/review/agents/
│       ├── SecurityReviewAgent.java
│       ├── PerformanceReviewAgent.java
│       ├── DddReviewAgent.java
│       ├── TestCoverageAgent.java
│       └── StyleReviewAgent.java
│
├── review-server/          ← 服务端（Orchestrator + 平台集成）
│   └── src/main/java/com/example/review/server/
│       ├── ReviewApplication.java
│       ├── controller/
│       │   ├── GitLabWebhookController.java
│       │   └── GitHubWebhookController.java
│       ├── orchestrator/
│       │   ├── ReviewOrchestrator.java
│       │   └── ReviewAggregator.java
│       ├── platform/
│       │   ├── PlatformClient.java      ← 接口
│       │   ├── GitLabPlatformClient.java
│       │   └── GitHubPlatformClient.java
│       ├── config/
│       │   ├── LlmConfig.java
│       │   ├── ThreadPoolConfig.java
│       │   └── ReviewRuleConfig.java
│       ├── service/
│       │   ├── ReviewRuleService.java
│       │   ├── ReviewHistoryService.java
│       │   └── NotificationService.java
│       └── repository/
│           ├── ReviewRuleRepository.java
│           └── ReviewHistoryRepository.java
│
└── src/test/               ← 测试
    ├── unit/
    │   ├── SecurityReviewAgentTest.java
    │   ├── ReviewOrchestratorTest.java
    │   └── ReviewAggregatorTest.java
    └── integration/
        └── EndToEndReviewIT.java   ← WireMock + Testcontainers
```

**模块依赖关系**：

```1:a:java-learning/85-SpringAI搭建内部CodeReviewAgent.md
review-server  ──► review-subagents  ──► review-core
                              │
                              └──────────────────► review-core
```

---

## 5. 核心代码（完整可运行）

### 5.1 父工程 `pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example.review</groupId>
    <artifactId>spring-ai-code-review-agent</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.4</version>
        <relativePath/>
    </parent>

    <properties>
        <java.version>17</java.version>
        <spring-ai.version>1.0.0-M6</spring-ai.version>
        <gitlab4j.version>25.1.0</gitlab4j.version>
        <github-api.version>1.318</github-api.version>
    </properties>

    <modules>
        <module>review-core</module>
        <module>review-subagents</module>
        <module>review-server</module>
    </modules>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

### 5.2 `review-core` 模块

#### 5.2.1 严重度枚举 `ReviewSeverity.java`

```java
package com.example.review.core.model;

public enum ReviewSeverity {
    BLOCKER,    // 阻塞合并（致命：安全漏洞、数据丢失风险）
    CRITICAL,   // 严重（性能、并发、安全）
    MAJOR,      // 重要（DDD 边界、事务边界）
    MINOR,      // 次要（风格、可读性）
    INFO;       // 提示

    public boolean isBlocker() {
        return this == BLOCKER || this == CRITICAL;
    }
}
```

#### 5.2.2 单条评审意见 `ReviewComment.java`

```java
package com.example.review.core.model;

public record ReviewComment(
        String agent,          // 哪个 Agent 提出的
        ReviewSeverity severity,// 严重度
        String file,           // 文件路径
        int line,              // 行号（0 表示文件级）
        String message,        // 评论正文（Markdown）
        String suggestion      // 修改建议
) {
    public static ReviewComment fileLevel(String agent, ReviewSeverity severity,
                                          String file, String message, String suggestion) {
        return new ReviewComment(agent, severity, file, 0, message, suggestion);
    }
}
```

#### 5.2.3 PR 上下文 `PullRequestContext.java`

```java
package com.example.review.core.model;

import java.util.List;

/**
 * PR 上下文：包含 diff + 元数据 + 受影响文件清单。
 * 由 PlatformClient 在 Webhook 触发时填充。
 */
public record PullRequestContext(
        String platform,          // gitlab / github
        String repoFullName,      // group/project 或 owner/repo
        int prNumber,
        String sourceBranch,
        String targetBranch,
        String author,
        String title,
        String description,
        String diff,              // 完整 diff（可能被截断）
        List<String> changedFiles,// 受影响文件
        int additions,
        int deletions
) {
    public boolean isLarge() {
        return diff != null && diff.length() > 100_000; // > 100KB 视为大 PR
    }
}
```

#### 5.2.4 完整评审报告 `ReviewReport.java`

```java
package com.example.review.core.model;

import java.time.Instant;
import java.util.List;

public record ReviewReport(
        String repoFullName,
        int prNumber,
        List<ReviewComment> comments,
        ReviewSummary summary,
        Instant reviewedAt,
        long costMillis
) {
    public record ReviewSummary(
            int total,
            int blockerCount,
            int criticalCount,
            int majorCount,
            int minorCount,
            int infoCount
    ) {}

    public boolean shouldBlockMerge() {
        return summary.blockerCount() > 0 || summary.criticalCount() > 0;
    }
}
```

#### 5.2.5 Agent 基类 `CodeReviewAgent.java`

```java
package com.example.review.core.agent;

import com.example.review.core.model.PullRequestContext;
import com.example.review.core.model.ReviewComment;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * 所有 SubAgent 的抽象基类。
 * 子类只需提供：name + systemPrompt + ChatClient（绑定工具集）。
 */
public abstract class CodeReviewAgent {

    /** Agent 名称（用于结果归类） */
    public abstract String name();

    /** System Prompt：定义 Agent 的角色、关注点、输出规范 */
    public abstract String systemPrompt();

    /** Agent 拥有的工具集（git_diff / read_file / grep ...） */
    protected List<Object> tools() { return List.of(); }

    @Autowired
    protected ChatClient.Builder chatClientBuilder;

    /** 核心方法：审查一个 PR 上下文，返回评论列表 */
    public List<ReviewComment> review(PullRequestContext ctx) {
        ChatClient client = chatClientBuilder
                .defaultSystem(systemPrompt())
                .defaultTools(tools().toArray())
                .build();

        String userPrompt = """
                请审查以下 Pull Request，并按 JSON 输出评论数组。

                ## PR 元信息
                - 仓库：%s
                - PR 号：#%d
                - 标题：%s
                - 作者：%s
                - 文件变更数：%d (+%d -%d)

                ## 输出格式（严格 JSON，不要任何额外文本）：
                ```json
                [
                  {
                    "file": "相对路径",
                    "line": 0,
                    "severity": "BLOCKER|CRITICAL|MAJOR|MINOR|INFO",
                    "message": "问题描述（Markdown）",
                    "suggestion": "修改建议（Markdown）"
                  }
                ]
                ```

                ## 关键规则
                1. 找不到问题时返回空数组 []
                2. line 必须真实存在于 diff 中（不要编造行号）
                3. BLOCKER/CRITICAL 只用于真正严重的问题
                4. 最多输出 20 条评论

                ## Diff:
                ```
                %s
                ```
                """.formatted(
                ctx.repoFullName(), ctx.prNumber(), ctx.title(), ctx.author(),
                ctx.changedFiles().size(), ctx.additions(), ctx.deletions(),
                ctx.diff() == null ? "" : truncate(ctx.diff(), 50_000)
        );

        String raw = client.prompt()
                .user(userPrompt)
                .call()
                .content();

        return parseComments(raw);
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "\n...(diff truncated)...";
    }

    /** 把 LLM 返回的 JSON 解析成评论列表 */
    protected List<ReviewComment> parseComments(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        // 剥 Markdown 代码块
        String json = raw;
        int fenceStart = raw.indexOf("```json");
        if (fenceStart >= 0) {
            int fenceEnd = raw.indexOf("```", fenceStart + 7);
            if (fenceEnd > fenceStart) {
                json = raw.substring(fenceStart + 7, fenceEnd);
            }
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json,
                        new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            // LLM 偶尔返回非 JSON，降级为空列表（不阻塞流程）
            return List.of();
        }
    }
}
```

#### 5.2.6 工具集：GitTools / FileTools / GrepTools

```java
package com.example.review.core.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;

/**
 * Agent 可调用的工具集（Function Calling）。
 * 每个 @Tool 方法对应 LLM 的一个可调用能力。
 */
@Component
public class GitTools {

    @Tool(description = "读取指定文件的完整内容（相对仓库根目录）")
    public String readFile(@ToolParam(description = "文件相对路径") String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    @Tool(description = "在整个仓库中搜索包含关键字的文件路径")
    public String grep(
            @ToolParam(description = "搜索关键字（支持正则）") String pattern,
            @ToolParam(description = "文件后缀过滤，如 .java") String fileExtension
    ) {
        StringBuilder sb = new StringBuilder();
        try (var stream = Files.walk(Paths.get("."))) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> fileExtension == null || p.toString().endsWith(fileExtension))
                  .forEach(p -> {
                      try {
                          if (Files.readString(p).contains(pattern)) {
                              sb.append(p).append("\n");
                          }
                      } catch (IOException ignored) {}
                  });
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
        return sb.length() == 0 ? "No matches" : sb.toString();
    }

    @Tool(description = "获取当前仓库最近 5 次提交记录")
    public String recentCommits() {
        try {
            return new ProcessBuilder("git", "log", "--oneline", "-5")
                    .redirectErrorStream(true)
                    .start()
                    .inputStream()
                    .transferTo(java.io.OutputStream.nullOutputStream());
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    @Tool(description = "获取指定 commit 的变更明细")
    public String commitDiff(@ToolParam(description = "commit SHA") String sha) {
        try {
            return new ProcessBuilder("git", "show", "--stat", sha)
                    .redirectErrorStream(true)
                    .start()
                    .inputStream()
                    .transferTo(java.io.OutputStream.nullOutputStream());
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
```

> **安全提示**：所有工具仅 `read`，**绝不给 Agent `write`/`push`/`merge`** 权限。

---

## 6. 5 个 SubAgent 实现

### 6.1 SecurityAgent 安全审查

```java
package com.example.review.agents;

import com.example.review.core.agent.CodeReviewAgent;
import org.springframework.stereotype.Component;

@Component
public class SecurityReviewAgent extends CodeReviewAgent {

    @Override
    public String name() { return "Security"; }

    @Override
    public String systemPrompt() {
        return """
                你是资深安全工程师，专精 OWASP Top 10。
                你的职责是审查 Java 代码的安全漏洞，**只关注以下问题**：

                ## 安全审查清单
                1. SQL 注入：字符串拼接 SQL → 必须用 PreparedStatement / 参数化
                2. XSS：直接拼接用户输入到 HTML / JS / URL
                3. 密钥硬编码：API Key、密码、Token 出现在代码里
                4. 敏感日志：打印密码、身份证、银行卡、手机号
                5. 未授权访问：Controller 没有鉴权注解
                6. 反序列化漏洞：ObjectInputStream / readObject
                7. 路径穿越：用户输入作为文件路径
                8. SSRF：用户输入的 URL 直接请求
                9. CSRF：表单/接口缺少 CSRF Token
                10. 不安全的随机数：new Random() 用于安全场景

                ## 重要约束
                - 找不到安全问题 → 返回 []
                - 优先报 SQL 注入 / 密钥硬编码 / 敏感日志
                - 其他问题（性能/风格）由其他 Agent 负责，你不要管
                """;
    }
}
```

### 6.2 PerformanceAgent 性能审查

```java
package com.example.review.agents;

import com.example.review.core.agent.CodeReviewAgent;
import org.springframework.stereotype.Component;

@Component
public class PerformanceReviewAgent extends CodeReviewAgent {

    @Override
    public String name() { return "Performance"; }

    @Override
    public String systemPrompt() {
        return """
                你是性能调优专家，专精 JVM + 数据库 + 高并发。
                你的职责是审查 Java 代码的性能问题，**只关注以下问题**：

                ## 性能审查清单
                1. N+1 查询：循环里调 findById / findByXxx
                2. 慢 SQL：缺少索引、SELECT *、IN 子句过大（>1000）
                3. 循环内 IO：循环里发 HTTP、调 RPC、读文件
                4. 内存泄漏：static Map 无清理、ThreadLocal 不 remove、Stream 不 close
                5. 不必要序列化：大对象 toString() 用于日志、JSON 序列化整个实体
                6. 锁粒度过粗：synchronized 方法、Long 锁整个对象
                7. 线程池滥用：每次 new Thread()、Executors.newCachedThreadPool
                8. 大对象分配：在循环中囤大 List/Map、字符串拼接
                9. 数据库事务过长：事务里调外部 HTTP/RPC
                10. 缺少缓存：高频读 + 低频写未加缓存

                ## 重要约束
                - 找不到性能问题 → 返回 []
                - 不要报告功能错误（其他 Agent 负责）
                - 行号必须真实存在于 diff
                """;
    }
}
```

### 6.3 DddAgent DDD 规范审查

```java
package com.example.review.agents;

import com.example.review.core.agent.CodeReviewAgent;
import org.springframework.stereotype.Component;

@Component
public class DddReviewAgent extends CodeReviewAgent {

    @Override
    public String name() { return "DDD"; }

    @Override
    public String systemPrompt() {
        return """
                你是 DDD 领域驱动设计专家，10 年经验。
                你的职责是审查代码的领域建模质量，**只关注以下问题**：

                ## DDD 审查清单
                1. 贫血模型：实体只有 getter/setter，业务逻辑在 Service
                2. 聚合根越界：聚合根方法修改外部对象 / 调外部服务
                3. 事务边界错误：跨聚合用同一事务、事务过大
                4. 仓储泄露：Service 直接调 Repository.save() 而非聚合根方法
                5. 领域服务职责混乱：业务逻辑放在 Controller / Util
                6. 值对象滥用：用 String/Long 表示 Money/DateRange/Email
                7. 缺少防腐层（ACL）：直接依赖外部系统的 Entity / DTO
                8. 缺少领域事件：状态变更后未发事件
                9. 领域逻辑写在 SQL：用数据库函数 / 存储过程实现业务规则
                10. Service 命名不规范：OrderService 既做查询又做命令（CQRS 违反）

                ## 重要约束
                - 找不到 DDD 问题 → 返回 []
                - 不要重复其他 Agent 的职责
                - 给出具体的"如何改造"建议
                """;
    }
}
```

### 6.4 TestCoverageAgent 测试审查

```java
package com.example.review.agents;

import com.example.review.core.agent.CodeReviewAgent;
import org.springframework.stereotype.Component;

@Component
public class TestCoverageAgent extends CodeReviewAgent {

    @Override
    public String name() { return "TestCoverage"; }

    @Override
    public String systemPrompt() {
        return """
                你是测试架构师，专精 JUnit 5 + Mockito + Spring Boot Test。
                你的职责是审查测试质量和覆盖度，**只关注以下问题**：

                ## 测试审查清单
                1. 新增 public 方法无对应测试
                2. 测试缺少断言（只调方法不 assert）
                3. 测试覆盖了正常路径但缺少异常路径
                4. Mockito 使用错误：verify(mock) 放在别处、ArgumentCaptor 滥用
                5. 测试耦合：测试间共享 static 状态、测试顺序依赖
                6. 慢测试：单元测试连真实数据库、发 HTTP
                7. 不稳定的断言：依赖 Thread.sleep、System.currentTimeMillis
                8. 测试不命名：@DisplayName 缺失、测试名无业务语义
                9. Given-When-Then 缺失：测试结构混乱
                10. 边界用例缺失：null、空集合、超大值、并发

                ## 重要约束
                - 找不到测试问题 → 返回 []
                - 优先报告"核心业务方法无测试"和"测试无断言"
                """;
    }
}
```

### 6.5 StyleAgent 风格审查

```java
package com.example.review.agents;

import com.example.review.core.agent.CodeReviewAgent;
import org.springframework.stereotype.Component;

@Component
public class StyleReviewAgent extends CodeReviewAgent {

    @Override
    public String name() { return "Style"; }

    @Override
    public String systemPrompt() {
        return """
                你是代码风格审查专家，对齐 Alibaba Java Coding Guidelines。
                你的职责是审查代码风格、可读性，**只关注以下问题**：

                ## 风格审查清单
                1. 命名不规范：类名小写、方法名 Pascal、字段名含拼音
                2. 缺少注释：public 方法无 Javadoc
                3. 注释不当：注释过时、注释与代码不一致
                4. 方法过长：单方法超过 80 行
                5. 类过大：单类超过 500 行
                6. 参数过多：单方法超过 5 个参数
                7. 魔法值：代码中直接出现 100 / 0.95 / "PENDING" 等
                8. Lombok 滥用：@Data 同时含业务字段 + 大量关联
                9. 注释掉的代码：// old logic 长期保留
                10. print 调试：System.out.println / log.debug 未清理

                ## 重要约束
                - 找不到风格问题 → 返回 []
                - 风格问题严重度默认 MINOR，注释缺失才 MAJOR
                """;
    }
}
```

---

## 7. Orchestrator 并行调度

### 7.1 `ReviewOrchestrator.java`

```java
package com.example.review.server.orchestrator;

import com.example.review.core.agent.CodeReviewAgent;
import com.example.review.core.model.PullRequestContext;
import com.example.review.core.model.ReviewComment;
import com.example.review.core.model.ReviewReport;
import com.example.review.core.tool.GitTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * 编排器：并行调度所有 SubAgent，整合最终报告。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewOrchestrator {

    private final List<CodeReviewAgent> agents;  // Spring 自动注入所有子类
    private final GitTools gitTools;
    private final ReviewAggregator aggregator;
    private final NotificationService notifier;

    private final ExecutorService agentPool = Executors.newFixedThreadPool(5);

    @Async("reviewExecutor")
    public CompletableFuture<ReviewReport> review(PullRequestContext ctx) {
        log.info("[Review Start] {}/{} PR#{}", ctx.platform(), ctx.repoFullName(), ctx.prNumber());
        long start = System.currentTimeMillis();

        // 1. 并行调度所有 Agent
        List<CompletableFuture<List<ReviewComment>>> futures = agents.stream()
                .map(agent -> CompletableFuture
                        .supplyAsync(() -> safeReview(agent, ctx), agentPool)
                        .orTimeout(120, TimeUnit.SECONDS)
                        .exceptionally(ex -> {
                            log.error("Agent {} 审查失败", agent.name(), ex);
                            return List.of();
                        }))
                .toList();

        // 2. 等待所有 Agent 完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        List<ReviewComment> allComments = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .toList();

        // 3. 聚合 + 去重 + 严重度评级
        ReviewReport report = aggregator.aggregate(ctx, allComments,
                Duration.ofMillis(System.currentTimeMillis() - start).toMillis());

        log.info("[Review End] {}/{} PR#{} - {} comments, blocker={}",
                ctx.platform(), ctx.repoFullName(), ctx.prNumber(),
                report.summary().total(), report.summary().blockerCount());

        // 4. 异步通知
        if (report.shouldBlockMerge()) {
            notifier.blockMergeAlert(ctx, report);
        }
        return CompletableFuture.completedFuture(report);
    }

    private List<ReviewComment> safeReview(CodeReviewAgent agent, PullRequestContext ctx) {
        try {
            return agent.review(ctx);
        } catch (Exception e) {
            log.error("Agent {} 异常", agent.name(), e);
            return List.of();
        }
    }
}
```

### 7.2 `ReviewAggregator.java` 聚合去重

```java
package com.example.review.server.orchestrator;

import com.example.review.core.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * 聚合：把多个 Agent 的结果合并去重 + 升级严重度。
 */
@Component
@RequiredArgsConstructor
public class ReviewAggregator {

    public ReviewReport aggregate(PullRequestContext ctx,
                                   List<ReviewComment> comments,
                                   long costMillis) {
        // 1. 同位置去重（多 Agent 报同一行同一问题 → 取最严重）
        Map<String, ReviewComment> dedup = new LinkedHashMap<>();
        for (ReviewComment c : comments) {
            String key = c.file() + ":" + c.line() + ":" + truncate(c.message(), 50);
            ReviewComment existing = dedup.get(key);
            if (existing == null || c.severity().ordinal() < existing.severity().ordinal()) {
                dedup.put(key, c);
            }
        }

        List<ReviewComment> unique = new ArrayList<>(dedup.values());

        // 2. 统计
        int blocker = 0, critical = 0, major = 0, minor = 0, info = 0;
        for (ReviewComment c : unique) {
            switch (c.severity()) {
                case BLOCKER -> blocker++;
                case CRITICAL -> critical++;
                case MAJOR -> major++;
                case MINOR -> minor++;
                case INFO -> info++;
            }
        }

        return new ReviewReport(
                ctx.repoFullName(), ctx.prNumber(), unique,
                new ReviewReport.ReviewSummary(
                        unique.size(), blocker, critical, major, minor, info),
                Instant.now(), costMillis
        );
    }

    private String truncate(String s, int n) {
        return s == null ? "" : (s.length() <= n ? s : s.substring(0, n));
    }
}
```

---

## 8. GitLab/GitHub 平台集成

### 8.1 平台客户端接口

```java
package com.example.review.server.platform;

import com.example.review.core.model.PullRequestContext;
import com.example.review.core.model.ReviewComment;
import com.example.review.core.model.ReviewReport;

import java.util.List;

public interface PlatformClient {

    /** 是否支持该平台 */
    boolean supports(String platform);

    /** 拉取 PR 上下文（diff + 元数据） */
    PullRequestContext fetchContext(String repoFullName, int prNumber);

    /** 把评审意见写到 PR/MR 评论 */
    void postComments(String repoFullName, int prNumber, ReviewReport report);

    /** 设置 MR/PR 状态（Success / Failed / Pending） */
    void setCommitStatus(String repoFullName, String sha,
                          String state, String description, String targetUrl);
}
```

### 8.2 GitLab 平台客户端

```java
package com.example.review.server.platform;

import com.example.review.core.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.models.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitLabPlatformClient implements PlatformClient {

    @Value("${gitlab.token}")
    private String token;

    @Value("${gitlab.url}")
    private String gitlabUrl;

    private GitLabApi api() {
        return new GitLabApi(gitlabUrl, token);
    }

    @Override
    public boolean supports(String platform) { return "gitlab".equalsIgnoreCase(platform); }

    @Override
    public PullRequestContext fetchContext(String repoFullName, int prNumber) {
        try {
            GitLabApi gitLab = api();
            MergeRequest mr = gitLab.getMergeRequestApi().getMergeRequest(
                    repoFullName, (long) prNumber);

            // 拉 diff
            String diff = gitLab.getMergeRequestApi()
                    .getMergeRequestDiff(repoFullName, (long) prNumber).long();

            // 拉变更文件清单
            List<String> files = gitLab.getMergeRequestApi()
                    .getMergeRequestChanges(repoFullName, (long) prNumber)
                    .getChanges().stream()
                    .map(Change::getNewPath)
                    .toList();

            return new PullRequestContext(
                    "gitlab", repoFullName, prNumber,
                    mr.getSourceBranch(), mr.getTargetBranch(),
                    mr.getAuthor().getUsername(),
                    mr.getTitle(), mr.getDescription(),
                    diff, files, 0, 0
            );
        } catch (Exception e) {
            throw new RuntimeException("拉取 GitLab MR 失败", e);
        }
    }

    @Override
    public void postComments(String repoFullName, int prNumber, ReviewReport report) {
        try {
            GitLabApi gitLab = api();

            // 1. 汇总评论（一个顶层 note）
            String summary = formatSummary(report);
            gitLab.getMergeRequestApi().createMergeRequestNote(
                    repoFullName, (long) prNumber, summary);

            // 2. 每条评论作为 inline discussion
            for (ReviewComment c : report.comments()) {
                if (c.line() > 0) {
                    var discussion = gitLab.getDiscussionsApi()
                            .createMergeRequestDiscussion(repoFullName, (long) prNumber,
                                    new DiscussionPayload()
                                            .withPosition(new Position()
                                                    .withPositionType("text")
                                                    .withNewPath(c.file())
                                                    .withNewLine(String.valueOf(c.line()))));
                    // 注：实际项目里需将整个 GitLab SDK 引入并填齐所有必填字段，
                    //     此处只展示骨架，正式版请查 gitlab4j-api 官方 API。
                }
            }

            // 4. 设置 commit status（决定是否阻塞合并）
            String sha = gitLab.getMergeRequestApi()
                    .getMergeRequest(repoFullName, (long) prNumber).getSha();
            String state = report.shouldBlockMerge() ? "failed" : "success";
            gitLab.getCommitsApi().addCommitStatus(
                    repoFullName, sha, state, report.summary().total() + " issues",
                    null, "review-agent", state.equals("failed"));

        } catch (Exception e) {
            log.error("回写 GitLab 失败", e);
        }
    }

    @Override
    public void setCommitStatus(String repoFullName, String sha,
                                 String state, String description, String targetUrl) {
        try {
            api().getCommitsApi().addCommitStatus(
                    repoFullName, sha, state, description, targetUrl, "review-agent", false);
        } catch (Exception e) {
            log.error("设置 commit status 失败", e);
        }
    }

    private String formatSummary(ReviewReport r) {
        var s = r.summary();
        return """
                ## 🤖 Code Review Agent 报告

                | 类型 | 数量 |
                |---|---|
                | 🔴 Blocker | %d |
                | 🟠 Critical | %d |
                | 🟡 Major | %d |
                | 🔵 Minor | %d |
                | ⚪ Info | %d |
                | **合计** | **%d** |

                %s

                ---
                ⏱ 审查耗时 %d ms · Agent: %s
                """.formatted(s.blockerCount(), s.criticalCount(),
                s.majorCount(), s.minorCount(), s.infoCount(), s.total(),
                r.shouldBlockMerge() ? "⛔ **本次 PR 包含严重问题，建议暂缓合并**" : "✅ 未发现严重问题",
                r.costMillis(),
                "Security / Performance / DDD / TestCoverage / Style");
    }
}
```

### 8.3 GitHub 平台客户端

```java
package com.example.review.server.platform;

import com.example.review.core.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubPlatformClient implements PlatformClient {

    @Value("${github.token}")
    private String token;

    private GitHub github;

    private GitHub gh() throws Exception {
        if (github == null) {
            github = new GitHubBuilder().withOAuthToken(token).build();
        }
        return github;
    }

    @Override
    public boolean supports(String platform) { return "github".equalsIgnoreCase(platform); }

    @Override
    public PullRequestContext fetchContext(String repoFullName, int prNumber) {
        try {
            GHRepository repo = gh().getRepository(repoFullName);
            GHPullRequest pr = repo.getPullRequest(prNumber);

            String diff = pr.getDiff();
            List<String> files = pr.getFiles().stream()
                    .map(GHFile::getFileName).toList();

            return new PullRequestContext(
                    "github", repoFullName, prNumber,
                    pr.getHead().getRef(), pr.getBase().getRef(),
                    pr.getUser().getLogin(),
                    pr.getTitle(), pr.getBody(),
                    diff, files, pr.getAdditions(), pr.getDeletions()
            );
        } catch (Exception e) {
            throw new RuntimeException("拉取 GitHub PR 失败", e);
        }
    }

    @Override
    public void postComments(String repoFullName, int prNumber, ReviewReport report) {
        try {
            GHPullRequest pr = gh().getRepository(repoFullName).getPullRequest(prNumber);
            // 顶层 issue 评论
            pr.comment(formatSummary(report));
            // inline review comments
            for (ReviewComment c : report.comments()) {
                if (c.line() > 0) {
                    pr.createReview()
                      .comment(c.message() + "\n\n**建议**：\n" + c.suggestion(),
                               c.file(), c.line())
                      .create();
                }
            }
            // commit status
            String state = report.shouldBlockMerge() ? GHCommitState.ERROR : GHCommitState.SUCCESS;
            repo.createCommitStatus(pr.getHead().getSha(), state,
                    report.summary().total() + " issues", "review-agent", "agent-report");
        } catch (Exception e) {
            log.error("回写 GitHub 失败", e);
        }
    }

    @Override
    public void setCommitStatus(String repoFullName, String sha,
                                 String state, String description, String targetUrl) {
        try {
            GHRepository repo = gh().getRepository(repoFullName);
            GHCommitState ghState = switch (state) {
                case "success" -> GHCommitState.SUCCESS;
                case "pending" -> GHCommitState.PENDING;
                default -> GHCommitState.ERROR;
            };
            repo.createCommitStatus(sha, ghState, description, targetUrl, "review-agent");
        } catch (Exception e) {
            log.error("设置 GitHub commit status 失败", e);
        }
    }

    private String formatSummary(ReviewReport r) {
        var s = r.summary();
        return """
                ## 🤖 Code Review Agent Report

                | Severity | Count |
                |---|---|
                | 🔴 Blocker | %d |
                | 🟠 Critical | %d |
                | 🟡 Major | %d |
                | 🔵 Minor | %d |
                | ⚪ Info | %d |

                %s

                ---
                ⏱ %d ms · Agents: Security / Performance / DDD / TestCoverage / Style
                """.formatted(s.blockerCount(), s.criticalCount(),
                s.majorCount(), s.minorCount(), s.infoCount(),
                r.shouldBlockMerge() ? "⛔ **This PR has critical issues**" : "✅ No critical issues found",
                r.costMillis());
    }
}
```

---

## 9. Webhook 接入与配置

### 9.1 Webhook Controller

```java
package com.example.review.server.controller;

import com.example.review.core.model.PullRequestContext;
import com.example.review.server.platform.PlatformClient;
import com.example.review.server.orchestrator.ReviewOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class ReviewWebhookController {

    private final List<PlatformClient> platformClients;
    private final ReviewOrchestrator orchestrator;

    /**
     * GitLab Webhook：MR 打开/更新/重建 时触发。
     * GitLab → Settings → Webhooks → URL: <this>/webhook/gitlab
     * 触发事件：Merge request events
     */
    @PostMapping("/gitlab")
    public Map<String, Object> onGitLab(@RequestBody GitLabWebhookPayload payload) {
        String event = payload.object_kind();
        if (!"merge_request".equals(event)) {
            return Map.of("status", "ignored", "reason", "not merge_request event");
        }

        var attrs = payload.object_attributes();
        String action = attrs.action();
        if (!List.of("open", "reopen", "update").contains(action)) {
            return Map.of("status", "ignored", "reason", "action=" + action);
        }

        String repoFullName = payload.project().path_with_namespace();
        int prNumber = attrs.iid();

        PlatformClient client = platformClients.stream()
                .filter(c -> c.supports("gitlab"))
                .findFirst().orElseThrow();

        PullRequestContext ctx = client.fetchContext(repoFullName, prNumber);
        orchestrator.review(ctx);

        return Map.of("status", "queued", "pr", prNumber, "repo", repoFullName);
    }

    /**
     * GitHub Webhook：PR opened/synchronize/reopened 时触发。
     * GitHub → Settings → Webhooks → URL: <this>/webhook/github
     */
    @PostMapping("/github")
    public Map<String, Object> onGitHub(@RequestBody GitHubWebhookPayload payload) {
        String action = payload.action();
        if (!List.of("opened", "synchronize", "reopened").contains(action)) {
            return Map.of("status", "ignored", "reason", "action=" + action);
        }
        String repoFullName = payload.repository().full_name();
        int prNumber = payload.pull_request().number();

        PlatformClient client = platformClients.stream()
                .filter(c -> c.supports("github"))
                .findFirst().orElseThrow();

        PullRequestContext ctx = client.fetchContext(repoFullName, prNumber);
        orchestrator.review(ctx);

        return Map.of("status", "queued", "pr", prNumber);
    }

    // ------- Webhook Payload DTO（精简） -------
    public record GitLabWebhookPayload(
            String object_kind,
            GitLabProject project,
            GitLabMR object_attributes
    ) {}
    public record GitLabProject(String path_with_namespace) {}
    public record GitLabMR(String action, int iid, String title, String description,
                            String source_branch, String target_branch,
                            Author author) {}
    public record Author(String username) {}

    public record GitHubWebhookPayload(
            String action,
            GitHubRepo repository,
            GitHubPR pull_request
    ) {}
    public record GitHubRepo(String full_name) {}
    public record GitHubPR(int number, String title, String body) {}
}
```

### 9.2 LLM 配置（多 Provider 切换）

```java
package com.example.review.server.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class LlmConfig {

    @Bean
    @Profile("openai")
    ChatClient openaiClient(@Value("${spring.ai.openai.api-key}") String key) {
        return ChatClient.builder()
                .defaultSystem("你是 Java 代码审查专家。")
                .build();
    }

    @Bean
    @Profile("dashscope")
    ChatClient dashscopeClient(@Value("${spring.ai.dashscope.api-key}") String key) {
        return ChatClient.builder()
                .defaultSystem("你是 Java 代码审查专家。")
                .build();
    }

    @Bean
    @Profile("deepseek")
    ChatClient deepseekClient(@Value("${spring.ai.deepseek.api-key}") String key) {
        return ChatClient.builder()
                .defaultSystem("你是 Java 代码审查专家。")
                .build();
    }

    @Bean
    @Profile("ollama")
    ChatClient ollamaClient() {
        return ChatClient.builder()
                .defaultSystem("你是 Java 代码审查专家。")
                .build();
    }
}
```

### 9.3 `application.yml`

```yaml
spring:
  application:
    name: review-agent
  profiles:
    active: openai   # openai / dashscope / deepseek / ollama

  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o
          temperature: 0.1   # 低温度 = 更确定的审查
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      chat:
        options:
          model: qwen-plus
          temperature: 0.1
    deepseek:
      api-key: ${DEEPSEEK_API_KEY}
      chat:
        options:
          model: deepseek-chat
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: qwen2.5-coder:7b

gitlab:
  url: ${GITLAB_URL}
  token: ${GITLAB_TOKEN}

github:
  token: ${GITHUB_TOKEN}

review:
  enabled-agents:
    - Security
    - Performance
    - DDD
    - TestCoverage
    - Style
  timeout-seconds: 120
  diff-truncate-bytes: 50000
  block-on:
    - BLOCKER
    - CRITICAL
  notify:
    feishu-webhook: ${FEISHU_WEBHOOK:}
    dingtalk-webhook: ${DINGTALK_WEBHOOK:}

logging:
  level:
    com.example.review: DEBUG
    org.springframework.ai: INFO
```

---

## 10. 实战：从 PR 到评论的端到端示例

### 10.1 一个会触发审查的"差 PR"

```java
// src/main/java/com/example/order/OrderService.java
@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    // 问题 1: SQL 注入
    public List<Order> searchByName(String name) {
        return jdbcTemplate.query(
            "SELECT * FROM orders WHERE customer_name = '" + name + "'",
            (rs, i) -> new Order(rs.getLong("id"), rs.getString("customer_name")));
    }

    // 问题 2: 密钥硬编码
    private String STRIPE_KEY = "sk_live_51Hxxxxxxxxxxxxxxxxxxxxxxxx";

    // 问题 3: N+1 查询
    public List<OrderDTO> listAll() {
        List<Order> all = orderRepository.findAll();
        List<OrderDTO> result = new ArrayList<>();
        for (Order o : all) {
            result.add(new OrderDTO(o.getId(), o.getCustomer().getName()));  // 每次都查 customer
        }
        return result;
    }

    // 问题 4: 循环内 HTTP
    public void notifyAll(List<Long> userIds) {
        for (Long id : userIds) {
            restTemplate.postForObject("https://api.notify.com/send", id, Void.class);
        }
    }

    // 问题 5: 内存泄漏
    private static final Map<Long, Order> CACHE = new ConcurrentHashMap<>();

    public void cache(Order o) {
        CACHE.put(o.getId(), o);  // 永远不清理
    }

    // 问题 6: 贫血模型
    public void pay(Order o, BigDecimal amount) {
        o.setPaidAmount(o.getPaidAmount().add(amount));
        o.setStatus("PAID");
        orderRepository.save(o);
    }

    // 问题 7: 缺少测试
    public void cancel(Order o, String reason) {
        o.setStatus("CANCELLED");
        orderRepository.save(o);
        log.info("order cancelled: " + reason);
    }
}
```

### 10.2 提交 PR 触发 Webhook

```bash
git add .
git commit -m "feat: order service improvements"
git push origin feature/order-fix
# 在 GitLab/GitHub 创建 MR/PR → webhook 自动触发
```

### 10.3 Agent 实际输出（示例）

```
🤖 Code Review Agent Report

| Severity | Count |
|---|---|
| 🔴 Blocker | 1 |
| 🟠 Critical | 2 |
| 🟡 Major | 3 |
| 🔵 Minor | 1 |

⛔ This PR has critical issues
---
⏱ 8.7s · Agents: Security / Performance / DDD / TestCoverage / Style

inline comments:
📍 OrderService.java:11 [BLOCKER] SQL 注入风险
   使用字符串拼接构建 SQL，恶意输入可执行任意 SQL。
   建议：用 NamedParameterJdbcTemplate + 参数化查询

📍 OrderService.java:17 [BLOCKER] 密钥硬编码
   Stripe 私钥出现在代码中，仓库泄露即资金损失。
   建议：从 Vault / 环境变量 / KMS 读取，禁止入仓

📍 OrderService.java:24 [CRITICAL] N+1 查询
   for 循环中调 o.getCustomer()，每行都查一次数据库。
   建议：用 @EntityGraph 或 JOIN FETCH 一次拉取

📍 OrderService.java:33 [CRITICAL] 循环内 HTTP
   for 循环里同步发 HTTP，N 个用户串行调用，延迟 O(N)。
   建议：用 CompletableFuture.allOf 并行，或 MQ 异步

📍 OrderService.java:40 [MAJOR] 静态 Map 内存泄漏
   静态 Map 无限增长，永不清理，最终 OOM。
   建议：用 Caffeine + TTL，或 Redis 缓存

📍 OrderService.java:47 [MAJOR] 贫血模型
   Order 实体只有 setter，业务逻辑放在 Service。
   建议：把 pay() 移到 Order 实体（充血模型）

📍 OrderService.java:57 [MAJOR] 缺少 cancel 测试
   核心 cancel 方法没有对应单元测试。
   建议：补上 @Test 覆盖正常路径 + 异常路径
```

### 10.4 最终 PR 页面效果

GitLab/GitHub PR 上会出现：
1. 顶层 issue 评论（带表格）
2. 每个 inline 评论（精确到行号）
3. Commit Status 失败（如果严重）
4. 飞书/钉钉通知（可选）

---

## 11. 测试策略

### 11.1 单元测试：Mock ChatClient

```java
package com.example.review.core;

import com.example.review.agents.SecurityReviewAgent;
import com.example.review.core.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SecurityReviewAgentTest {

    @Test
    void shouldParseJsonComments() {
        // Mock ChatClient
        SecurityReviewAgent agent = new SecurityReviewAgent() {
            @Override
            protected void init() {}  // 跳过 @Autowired
        };

        ChatClient mockClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec reqSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec respSpec = mock(ChatClient.CallResponseSpec.class);

        when(mockClient.prompt()).thenReturn(reqSpec);
        when(reqSpec.user(anyString())).thenReturn(reqSpec);
        when(reqSpec.call()).thenReturn(respSpec);
        when(respSpec.content()).thenReturn("""
                ```json
                [
                  {
                    "file": "OrderService.java",
                    "line": 11,
                    "severity": "BLOCKER",
                    "message": "SQL 注入",
                    "suggestion": "用参数化查询"
                  }
                ]
                ```""");

        agent.chatClientBuilder = mock(ChatClient.Builder.class);
        when(agent.chatClientBuilder.defaultSystem(anyString())).thenReturn(agent.chatClientBuilder);
        when(agent.chatClientBuilder.defaultTools(any(Object[].class))).thenReturn(agent.chatClientBuilder);
        when(agent.chatClientBuilder.build()).thenReturn(mockClient);

        PullRequestContext ctx = new PullRequestContext(
                "gitlab", "group/project", 1,
                "feat", "main", "alice",
                "test", null, "diff", List.of("OrderService.java"), 10, 5
        );

        List<ReviewComment> comments = agent.review(ctx);

        assertThat(comments).hasSize(1);
        assertThat(comments.get(0).severity()).isEqualTo(ReviewSeverity.BLOCKER);
        assertThat(comments.get(0).file()).isEqualTo("OrderService.java");
    }
}
```

### 11.2 集成测试：WireMock + Testcontainers

```java
@SpringBootTest
@Testcontainers
class EndToEndReviewIT {

    @Container
    static GenericContainer<?> gitlab = new GenericContainer<>("gitlab/gitlab-ce:16.0")
            .withEnv("GITLAB_ROOT_TOKEN", "test-token")
            .withExposedPorts(80);

    @Test
    void reviewShouldBlockCriticalIssues(@Autowired ReviewOrchestrator orchestrator) {
        // 给定：一个包含 SQL 注入的 PR
        PullRequestContext ctx = new PullRequestContext(
                "gitlab", "test/project", 1,
                "feat", "main", "alice",
                "Bug fix", null, SAMPLE_DIFF_WITH_SQL_INJECTION,
                List.of("OrderService.java"), 5, 2);

        // 当：触发审查
        ReviewReport report = orchestrator.review(ctx).join();

        // 那么：应发现 BLOCKER
        assertThat(report.shouldBlockMerge()).isTrue();
        assertThat(report.summary().blockerCount()).isGreaterThan(0);
    }
}
```

---

## 12. Docker Compose 一键部署

```yaml
version: '3.8'

services:
  review-agent:
    build: ./review-server
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=openai
      - OPENAI_API_KEY=${OPENAI_API_KEY}
      - GITLAB_URL=${GITLAB_URL}
      - GITLAB_TOKEN=${GITLAB_TOKEN}
      - GITHUB_TOKEN=${GITHUB_TOKEN}
    depends_on:
      - postgres
      - redis

  postgres:
    image: postgres:15
    environment:
      - POSTGRES_DB=review
      - POSTGRES_USER=review
      - POSTGRES_PASSWORD=review
    ports:
      - "5432:5432"
    volumes:
      - review-db:/var/lib/postgresql/data

  redis:
    image: redis:7
    ports:
      - "6379:6379"

volumes:
  review-db:
```

```bash
# 启动
docker-compose up -d

# 配置 GitLab Webhook
# Settings → Webhooks → URL: http://review-agent:8080/webhook/gitlab
# Trigger: Merge request events
```

---

## 13. 7 大踩坑（生产经验）

### 13.1 Diff 太大 → Token 超限

```yaml
# review-config.yml
review:
  diff-truncate-bytes: 50000  # 超 50KB 截断
  large-pr-strategy: skip     # 大 PR 跳过（也可选 summary）
```

最佳实践：超过 50KB 的 diff 改为只给"变更文件清单 + 关键代码片段"。

### 13.2 Agent 越权 → 安全隐患

**绝不给 Agent `write`/`push`/`merge`/`delete`** 权限。Agent 只有：
- ✅ `read_file`
- ✅ `grep` / `git_log` / `git_show`
- ❌ `write_file` / `git_commit` / `git_push` / `rm`

### 13.3 LLM 幻觉 → 假行号

```java
// 在 parseComments 里加严格校验
protected List<ReviewComment> parseComments(String raw) {
    // 1. JSON 解析
    // 2. 校验 line 必须在 diff 中真实存在（用 ctx.changedFiles + 简单正则）
    // 3. 校验 file 必须在 ctx.changedFiles 里
    // 4. 校验 severity 是合法枚举
    // 否则丢弃该评论
}
```

### 13.4 审查延迟 → 用户体验

- **并行调度**：5 个 Agent 必须并行（CompletableFuture.allOf）
- **超时控制**：单个 Agent 120s 超时，不能让 PR 一直挂着
- **降级**：单个 Agent 失败不影响其他，汇总时标记 partial

### 13.5 误报太多 → 用户关闭

- **加阈值**：同一规则同一文件 3 次误报 → 自动降为 INFO
- **白名单**：`/review-ignore sql-injection com/example/legacy/**`
- **可配置严重度**：每个仓库可关闭某些规则

### 13.6 Webhook 安全

```java
@PostMapping("/gitlab")
public Map onGitLab(@RequestBody String rawBody,
                     @RequestHeader("X-Gitlab-Token") String token) {
    if (!constantTimeEquals(token, expectedToken)) {
        throw new ResponseStatusException(401);
    }
    // ...
}
```

### 13.7 LLM 成本控制

```yaml
review:
  max-agents-per-pr: 5
  max-tokens-per-agent: 8000
  cache-ttl: 7d   # 同 SHA 7 天内不重复审查
```

实现：把 `repo + SHA + review-rules-version` 作为 Redis 缓存 key。

---

## 14. 5 道面试题

### Q1：Function Calling 的本质是什么？和 Prompt 工程有什么区别？

**答**：Function Calling 是 LLM 的"结构化输出能力扩展"——模型被训练成能输出符合 schema 的 JSON，外部系统解析后调用真实 API，再把结果回传。

- **Prompt 工程**：靠文本指令让 LLM 输出 JSON（不稳定，可能编字段名）
- **Function Calling**：靠 schema 约束输出 + 模型原生支持工具选择（稳定，可枚举）

Spring AI 通过 `@Tool` 注解 + JSON Schema 自动注入实现。

### Q2：多 Agent 协作有几种模式？各适合什么场景？

**答**：

| 模式 | 描述 | 适合场景 |
|---|---|---|
| **顺序链** | Agent1 → Agent2 → Agent3 | 流水线（分析 → 编码 → 测试） |
| **并行** | 同时调多个 Agent，合并结果 | **本案例**：多维度审查 |
| **层级** | 主 Agent 调度子 Agent | 复杂决策（CEO/Manager 模式） |
| **协同/辩论** | 多 Agent 互相 review | 高风险（医疗诊断、金融） |

本案例用 **并行**——5 个独立维度的审查互相不干扰。

### Q3：怎么避免 LLM 评审的"幻觉"（假行号、假文件）？

**答**：四道防线：

1. **结构化输出**：用 Function Calling 或 JSON Mode 强制 schema
2. **后置校验**：解析后校验 file 在 changedFiles、line 在 diff 中真实存在
3. **采样投票**：同一审查跑 2 次，结果一致才采纳
4. **人工反馈闭环**：误报 → 修正 prompt + 加白名单 → 下次自动避开

### Q4：Code Review Agent 的性能瓶颈在哪里？怎么优化？

**答**：

| 瓶颈 | 优化 |
|---|---|
| LLM 调用延迟 | 5 Agent 并行 + 流式响应（SSE） |
| Diff 太大 | 截断 + 关键文件优先（增量审查） |
| 同一 PR 反复触发 | Redis 缓存（commit SHA 维度） |
| Webhook 风暴 | 合并相邻 30s 内的触发（只审最新一次） |
| LLM 成本 | 小模型（Ollama）+ 大模型兜底 |

### Q5：如何评估 Code Review Agent 的"质量"？

**答**：三个指标：

1. **召回率（Recall）**：人工评审发现的严重问题中，Agent 报出了多少（越高越好）
2. **误报率（False Positive Rate）**：Agent 报出的问题中，人工认为是误报的比例（越低越好）
3. **阻塞率（Block Rate）**：被 Agent 阻塞合并的 PR 中，确实不该合并的比例（越高越有价值）

通过 A/B 测试：把 Agent 建议作为辅助，由人工决定是否采纳，3 个月后统计指标。

---

## 15. 进阶扩展方向

| 方向 | 实现思路 |
|---|---|
| **增量审查** | 只看 diff 不看全文件，节省 token |
| **本地规则补充** | Checkstyle/PMD/SpotBugs 先跑一遍，把违规行号喂给 LLM 提高准确率 |
| **学习反馈** | 用户标记"这条意见有误" → 入库 → 下次 prompt 自动避开 |
| **多语言支持** | Python/Go/TS Agent 平行扩展（每个语言独立 prompt） |
| **风险预测** | 历史数据训练模型，预判 PR 引入缺陷概率 |
| **自动修复** | 审查完直接生成 fix commit（需授权仓库 write） |
| **Slack/Teams 集成** | 严重问题直接 DM Reviewer |
| **审查 Dashboard** | Grafana + Prometheus 监控（评审耗时/成功率/Token 消耗） |

---

## 16. 与商用方案对比

| 维度 | 本方案 | Codacy | SonarQube + LLM | GitHub Copilot Reviews |
|---|---|---|---|---|
| 私有部署 | ✅ | ❌ SaaS | ✅ | ❌ SaaS |
| 中文审查 | ✅（prompt 中文） | ⚠️ | ⚠️ | ⚠️ |
| 成本 | ¥ 几元/千次 | $$$$ | $$ | $$$ |
| 可定制规则 | ✅ 完全 | ⚠️ 模板 | ⚠️ 模板 | ❌ |
| DDD 审查 | ✅（自定义 Agent） | ❌ | ❌ | ⚠️ 通用 |
| 学习曲线 | 中（懂 Spring AI） | 低 | 中 | 低 |
| 适用规模 | 中小团队 | 中大 | 中大 | 中小 |

**结论**：对**有 Java 研发团队 + 强 DDD 规范 + 数据不出网**的企业，自建 ROI 远超商用方案。

---

## 17. 一句话总结

> **Code Review Agent 不是要替代 Reviewer，而是要替代 Reviewer 最讨厌的 80% 重复劳动 —— 让 Senior 工程师把时间花在业务和架构上。**

---

## 18. 对应仓库章节

| 章节 | 内容 |
|---|---|
| 第八十四章 | AI Agent Harness 基础（Function Calling、ReAct、Plan-Execute） |
| **第八十五章** | **本篇**：Spring AI 完整 Code Review Agent 工程 |
| 第八十六章（预告） | AI 测试生成 Agent（基于 PR diff 自动补单元测试） |

---

## 附录：快速启动命令

```bash
# 1. 克隆代码
git clone <repo>/spring-ai-code-review-agent.git
cd spring-ai-code-review-agent

# 2. 设置环境变量
export OPENAI_API_KEY=sk-xxxxx
export GITLAB_URL=https://gitlab.example.com
export GITLAB_TOKEN=glpat-xxxxx

# 3. 启动
docker-compose up -d

# 4. 模拟触发（用 curl 模拟 GitLab Webhook）
curl -X POST http://localhost:8080/webhook/gitlab \
  -H "Content-Type: application/json" \
  -H "X-Gitlab-Token: <your-token>" \
  -d @sample-webhook.json

# 5. 查看日志
docker-compose logs -f review-agent
```