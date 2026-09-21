package com.example.review.core.model;

/**
 * 单条评审意见（一条评论）。
 *
 * @param agent       提出该评论的 Agent 名称
 * @param severity    严重度
 * @param file        文件相对路径
 * @param line        行号（0 表示文件级意见）
 * @param message     评论正文（支持 Markdown）
 * @param suggestion  修改建议
 */
public record ReviewComment(
        String agent,
        ReviewSeverity severity,
        String file,
        int line,
        String message,
        String suggestion
) {
    /**
     * 文件级意见快捷构造（行号为 0）。
     */
    public static ReviewComment fileLevel(String agent,
                                          ReviewSeverity severity,
                                          String file,
                                          String message,
                                          String suggestion) {
        return new ReviewComment(agent, severity, file, 0, message, suggestion);
    }
}