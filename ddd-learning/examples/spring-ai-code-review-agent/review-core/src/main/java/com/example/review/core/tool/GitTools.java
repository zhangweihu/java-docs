package com.example.review.core.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Agent 可调用的 Git / 文件 / 搜索工具（Function Calling）。
 * <p>
 * 所有方法均为 <strong>只读</strong>，绝不给 Agent write / push / merge 权限。
 */
@Component
public class GitTools {

    @Tool(description = "读取指定文件的完整内容（相对当前工作目录）")
    public String readFile(
            @ToolParam(description = "文件相对路径") String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    @Tool(description = "在整个仓库中搜索包含关键字的文件路径（默认搜 .java）")
    public String grep(
            @ToolParam(description = "搜索关键字（支持正则）") String pattern,
            @ToolParam(description = "文件后缀过滤，如 .java；留空搜所有文件") String fileExtension) {
        StringBuilder sb = new StringBuilder();
        try (Stream<Path> stream = Files.walk(Paths.get("."))) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> fileExtension == null || fileExtension.isBlank()
                          || p.toString().endsWith(fileExtension))
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
            return execProcess("git", "log", "--oneline", "-5");
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    @Tool(description = "获取指定 commit 的变更明细")
    public String commitDiff(
            @ToolParam(description = "commit SHA") String sha) {
        try {
            return execProcess("git", "show", "--stat", sha);
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    @Tool(description = "获取当前仓库的工作目录列表")
    public String listFiles() {
        try (Stream<Path> stream = Files.list(Paths.get("."))) {
            return stream.filter(Files::isDirectory)
                         .map(Path::toString)
                         .reduce("", (a, b) -> a + "\n" + b);
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private String execProcess(String... cmd) throws IOException {
        Process p = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();
        byte[] bytes = p.getInputStream().readAllBytes();
        return new String(bytes);
    }
}