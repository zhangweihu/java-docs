package com.example.review.core.agent;

import com.example.review.core.model.PullRequestContext;
import com.example.review.core.model.ReviewComment;
import com.example.review.core.model.ReviewSeverity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * 所有 SubAgent 的抽象基类。
 *
 * <p>子类只需提供：
 * <ul>
 *   <li>{@link #name()} — Agent 名称（用于结果归类）</li>
 *   <li>{@link #systemPrompt()} — System Prompt</li>
 *   <li>{@link #tools()} — 可选：Agent 拥有的工具集</li>
 * </ul>
 *
 * <p>调用 {@link #review(PullRequestContext)} 即可获得评审意见列表。
 */
@Slf4j
public abstract class CodeReviewAgent {

    protected static final int MAX_DIFF_LENGTH = 50_000;
    protected static final int MAX_COMMENTS_PER_AGENT = 20;

    @Autowired
    protected ChatClient.Builder chatClientBuilder;

    public abstract String name();

    public abstract String systemPrompt();

    protected List<Object> tools() {
        return List.of();
    }

    /**
     * 核心方法：审查一个 PR 上下文，返回评论列表。
     */
    public List<ReviewComment> review(PullRequestContext ctx) {
        ChatClient client = chatClientBuilder
                .defaultSystem(systemPrompt())
                .defaultTools(tools().toArray())
                .build();

        String userPrompt = buildPrompt(ctx);
        log.debug("[{}] 审查 {}/{} PR#{}", name(),
                ctx.platform(), ctx.repoFullName(), ctx.prNumber());

        String raw = client.prompt()
                .user(userPrompt)
                .call()
                .content();

        return parseComments(raw, ctx);
    }

    private String buildPrompt(PullRequestContext ctx) {
        return """
                请审查以下 Pull Request，并按 JSON 输出评论数组。

                ## PR 元信息
                - 仓库：%s
                - PR 号：#%d
                - 标题：%s
                - 作者：%s
                - 文件变更数：%d (+%d -%d)

                ## 输出格式（严格 JSON，不要任何额外文本）
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
                4. 最多输出 %d 条评论

                ## Diff:
                ```
                %s
                ```
                """.formatted(
                ctx.repoFullName(), ctx.prNumber(), ctx.title(), ctx.author(),
                ctx.changedFiles().size(), ctx.additions(), ctx.deletions(),
                MAX_COMMENTS_PER_AGENT,
                ctx.diff() == null ? "" : truncate(ctx.diff(), MAX_DIFF_LENGTH));
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "\n...(diff truncated)...";
    }

    /**
     * 把 LLM 返回的 JSON 解析成评论列表，并做基本校验。
     */
    protected List<ReviewComment> parseComments(String raw, PullRequestContext ctx) {
        if (raw == null || raw.isBlank()) return List.of();

        String json = stripCodeBlock(raw);
        try {
            List<ReviewComment> parsed = new ObjectMapper()
                    .readValue(json, new TypeReference<List<ReviewComment>>() {});
            return parsed.stream()
                    .filter(c -> isValid(c, ctx))
                    .limit(MAX_COMMENTS_PER_AGENT)
                    .toList();
        } catch (Exception e) {
            log.warn("[{}] 解析 LLM 输出失败：{}", name(), e.getMessage());
            return List.of();
        }
    }

    /** 校验评论合法性：文件必须在 changedFiles、line 必须 > 0、severity 合法 */
    private boolean isValid(ReviewComment c, PullRequestContext ctx) {
        if (c.file() == null || c.message() == null) return false;
        if (c.line() != 0 && !ctx.changedFiles().contains(c.file())) return false;
        try {
            ReviewSeverity.valueOf(c.severity().name());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String stripCodeBlock(String raw) {
        int fenceStart = raw.indexOf("```json");
        if (fenceStart < 0) {
            fenceStart = raw.indexOf("```");
        }
        if (fenceStart < 0) return raw.trim();

        int contentStart = raw.indexOf('\n', fenceStart);
        if (contentStart < 0) return raw.trim();

        int fenceEnd = raw.indexOf("```", contentStart);
        if (fenceEnd < 0) return raw.substring(contentStart).trim();
        return raw.substring(contentStart, fenceEnd).trim();
    }
}