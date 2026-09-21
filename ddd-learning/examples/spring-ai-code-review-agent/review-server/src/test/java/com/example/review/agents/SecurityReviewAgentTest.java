package com.example.review.agents;

import com.example.review.core.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * SecurityReviewAgent 单元测试：Mock ChatClient 验证 JSON 解析。
 */
class SecurityReviewAgentTest {

    @Test
    void shouldParseValidJsonComments() {
        SecurityReviewAgent agent = new SecurityReviewAgent();

        // Mock ChatClient 链路
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient client = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec reqSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec respSpec = mock(ChatClient.CallResponseSpec.class);

        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.defaultTools(any(Object[].class))).thenReturn(builder);
        when(builder.build()).thenReturn(client);
        when(client.prompt()).thenReturn(reqSpec);
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

        agent.chatClientBuilder = builder;

        PullRequestContext ctx = new PullRequestContext(
                "gitlab", "g/p", 1,
                "feat", "main", "alice",
                "test", null, "diff",
                List.of("OrderService.java"), 10, 5);

        List<ReviewComment> comments = agent.review(ctx);

        assertThat(comments).hasSize(1);
        assertThat(comments.get(0).severity()).isEqualTo(ReviewSeverity.BLOCKER);
        assertThat(comments.get(0).file()).isEqualTo("OrderService.java");
        assertThat(comments.get(0).message()).contains("SQL 注入");
    }

    @Test
    void shouldDropInvalidComments() {
        SecurityReviewAgent agent = new SecurityReviewAgent();

        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient client = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec reqSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec respSpec = mock(ChatClient.CallResponseSpec.class);

        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.defaultTools(any(Object[].class))).thenReturn(builder);
        when(builder.build()).thenReturn(client);
        when(client.prompt()).thenReturn(reqSpec);
        when(reqSpec.user(anyString())).thenReturn(reqSpec);
        when(reqSpec.call()).thenReturn(respSpec);
        // 包含一个合法评论 + 一个非法评论（文件不在 changedFiles）
        when(respSpec.content()).thenReturn("""
                ```json
                [
                  {"file": "OrderService.java", "line": 11, "severity": "BLOCKER", "message": "ok", "suggestion": "s"},
                  {"file": "GhostFile.java", "line": 99, "severity": "MAJOR", "message": "fake", "suggestion": "s"}
                ]
                ```""");

        agent.chatClientBuilder = builder;

        PullRequestContext ctx = new PullRequestContext(
                "gitlab", "g/p", 1,
                "feat", "main", "alice", "test", null, "diff",
                List.of("OrderService.java"), 1, 1);

        List<ReviewComment> comments = agent.review(ctx);

        assertThat(comments).hasSize(1);
        assertThat(comments.get(0).file()).isEqualTo("OrderService.java");
    }

    @Test
    void shouldReturnEmptyListWhenNoCodeBlock() {
        SecurityReviewAgent agent = new SecurityReviewAgent();
        agent.chatClientBuilder = mock(ChatClient.Builder.class);

        // 当 LLM 输出不是合法 JSON 时
        ChatClient client = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec reqSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec respSpec = mock(ChatClient.CallResponseSpec.class);

        when(agent.chatClientBuilder.defaultSystem(anyString())).thenReturn(agent.chatClientBuilder);
        when(agent.chatClientBuilder.defaultTools(any(Object[].class))).thenReturn(agent.chatClientBuilder);
        when(agent.chatClientBuilder.build()).thenReturn(client);
        when(client.prompt()).thenReturn(reqSpec);
        when(reqSpec.user(anyString())).thenReturn(reqSpec);
        when(reqSpec.call()).thenReturn(respSpec);
        when(respSpec.content()).thenReturn("随便写一些非 JSON 内容");

        PullRequestContext ctx = new PullRequestContext(
                "gitlab", "g/p", 1, "f", "main", "a", "t", null, "d",
                List.of("OrderService.java"), 1, 1);

        List<ReviewComment> comments = agent.review(ctx);
        assertThat(comments).isEmpty();
    }
}