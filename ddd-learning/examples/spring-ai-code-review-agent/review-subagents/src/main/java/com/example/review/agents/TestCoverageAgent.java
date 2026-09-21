package com.example.review.agents;

import com.example.review.core.agent.CodeReviewAgent;
import org.springframework.stereotype.Component;

/**
 * 测试覆盖度审查 Agent。
 * <p>
 * 关注：测试缺失、断言缺失、Mockito 使用、测试耦合、慢测试、不稳定断言、边界用例。
 */
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