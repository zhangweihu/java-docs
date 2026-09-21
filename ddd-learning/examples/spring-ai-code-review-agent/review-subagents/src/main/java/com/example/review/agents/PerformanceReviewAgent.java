package com.example.review.agents;

import com.example.review.core.agent.CodeReviewAgent;
import org.springframework.stereotype.Component;

/**
 * 性能审查 Agent。
 * <p>
 * 关注：N+1 查询、慢 SQL、循环内 IO、内存泄漏、序列化、锁粒度、线程池、事务时长、缓存策略。
 */
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
                8. 大对象分配：在循环中囤大 List/Map、字符串拼接用 + 而非 StringBuilder
                9. 数据库事务过长：事务里调外部 HTTP/RPC
                10. 缺少缓存：高频读 + 低频写未加缓存（@Cacheable 缺失）

                ## 重要约束
                - 找不到性能问题 → 返回 []
                - 不要报告功能错误（其他 Agent 负责）
                - 行号必须真实存在于 diff
                """;
    }
}