package com.example.review.agents;

import com.example.review.core.agent.CodeReviewAgent;
import org.springframework.stereotype.Component;

/**
 * 风格审查 Agent。
 * <p>
 * 关注：命名规范、注释、方法长度、类大小、参数数量、魔法值、Lombok 滥用。
 */
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