package vip.mate.dataagent.service.code.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import vip.mate.dataagent.service.code.CodeExecutorProperties;
import vip.mate.dataagent.service.code.CodeExecutorService;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 本地 Python 环境执行器
 * <p>
 * 借鉴 DataAgent 项目的 LocalCodePoolExecutorService 设计，
 * 通过 ProcessBuilder 调用本地 Python 解释器执行代码。
 * <p>
 * 执行流程：
 * 1. 创建临时工作目录
 * 2. 写入 script.py、stdin.txt、requirements.txt
 * 3. 如有 requirements 则先 pip install
 * 4. 执行 python3 script.py < stdin.txt
 * 5. 收集 stdout/stderr 返回结果
 */
@Slf4j
public class LocalCodeExecutorService implements CodeExecutorService {

    private final CodeExecutorProperties properties;

    /** 检测到的 Python 命令 */
    private final String resolvedPythonCommand;

    /** 检测到的 pip 命令 */
    private final String resolvedPipCommand;

    /** Python 命令候选列表（按优先级排序） */
    private static final String[] PYTHON_CANDIDATES = {"python3", "py", "python"};

    /** pip 命令候选列表（按优先级排序） */
    private static final String[] PIP_CANDIDATES = {"pip3", "pip"};

    public LocalCodeExecutorService(CodeExecutorProperties properties) {
        this.properties = properties;

        // 优先使用配置指定的命令，否则自动检测
        if (StringUtils.hasText(properties.getPythonCommand())) {
            this.resolvedPythonCommand = properties.getPythonCommand();
        } else {
            this.resolvedPythonCommand = detectCommand(PYTHON_CANDIDATES);
        }

        if (StringUtils.hasText(properties.getPipCommand())) {
            this.resolvedPipCommand = properties.getPipCommand();
        } else {
            this.resolvedPipCommand = detectCommand(PIP_CANDIDATES);
        }

        if (this.resolvedPythonCommand != null) {
            log.info("Python 执行器初始化完成, python={}, pip={}",
                    this.resolvedPythonCommand, this.resolvedPipCommand);
        } else {
            log.warn("未检测到可用的 Python 解释器，Python 分析功能将不可用");
        }
    }

    @Override
    public TaskResponse execute(TaskRequest request) {
        if (!isAvailable()) {
            return TaskResponse.exception("Python 执行环境不可用，请确保系统已安装 Python");
        }

        Path workDir = null;
        try {
            // 1. 创建临时工作目录
            workDir = Files.createTempDirectory(properties.getWorkDirPrefix());

            // 2. 写入文件
            Path scriptFile = workDir.resolve("script.py");
            Path stdinFile = workDir.resolve("stdin.txt");
            Path requirementFile = workDir.resolve("requirements.txt");

            Files.writeString(scriptFile, Optional.ofNullable(request.code()).orElse(""));
            Files.writeString(stdinFile, Optional.ofNullable(request.input()).orElse(""));
            Files.writeString(requirementFile, Optional.ofNullable(request.requirement()).orElse(""));

            // 3. 安装依赖（如有）
            if (resolvedPipCommand != null && StringUtils.hasText(request.requirement())) {
                installDependencies(requirementFile);
            }

            // 4. 执行 Python 代码
            return runPythonScript(scriptFile, stdinFile);

        } catch (Exception e) {
            log.error("Python 执行失败: {}", e.getMessage(), e);
            return TaskResponse.exception(e.getMessage());
        } finally {
            // 5. 清理临时目录
            cleanupWorkDir(workDir);
        }
    }

    @Override
    public boolean isAvailable() {
        return resolvedPythonCommand != null && properties.isEnabled();
    }

    /**
     * 安装 pip 依赖
     */
    private void installDependencies(Path requirementFile) {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    resolvedPipCommand, "install", "--no-cache-dir", "-r",
                    requirementFile.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            process = pb.start();

            boolean completed = process.waitFor(properties.getPipTimeoutSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                log.warn("pip 安装依赖超时");
            } else if (process.exitValue() != 0) {
                log.warn("pip 安装依赖返回非零退出码: {}", process.exitValue());
            }
        } catch (Exception e) {
            log.warn("pip 安装依赖失败: {}，继续尝试执行 Python 代码", e.getMessage());
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * 执行 Python 脚本
     */
    private TaskResponse runPythonScript(Path scriptFile, Path stdinFile) {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    resolvedPythonCommand, "-u", scriptFile.toAbsolutePath().toString()
            );
            pb.directory(scriptFile.getParent().toFile());
            pb.redirectInput(stdinFile.toFile());
            process = pb.start();

            // 异步读取 stdout 和 stderr
            StringWriter stdoutWriter = new StringWriter();
            StringWriter stderrWriter = new StringWriter();

            try (BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

                CompletableFuture<Void> stdoutFuture = CompletableFuture.runAsync(() -> {
                    try {
                        stdoutReader.transferTo(stdoutWriter);
                    } catch (IOException e) {
                        log.warn("读取 Python stdout 失败: {}", e.getMessage());
                    }
                });

                CompletableFuture<Void> stderrFuture = CompletableFuture.runAsync(() -> {
                    try {
                        stderrReader.transferTo(stderrWriter);
                    } catch (IOException e) {
                        log.warn("读取 Python stderr 失败: {}", e.getMessage());
                    }
                });

                // 等待进程完成，带超时
                boolean completed = process.waitFor(properties.getCodeTimeoutSeconds(), TimeUnit.SECONDS);
                if (!completed) {
                    process.destroyForcibly();
                    return TaskResponse.failure("", "Python 代码执行超时（"
                            + properties.getCodeTimeoutSeconds() + "秒），已终止");
                }

                // 等待输出读取完成
                CompletableFuture.allOf(stdoutFuture, stderrFuture).get(5, TimeUnit.SECONDS);
            }

            int exitCode = process.exitValue();
            String stdout = truncate(stdoutWriter.toString(), properties.getMaxStdOutLength());
            String stderr = truncate(stderrWriter.toString(), properties.getMaxStdErrLength());

            if (exitCode != 0) {
                log.warn("Python 执行返回非零退出码: {}, stderr: {}", exitCode, stderr);
                return TaskResponse.failure(stdout, stderr);
            }

            return TaskResponse.success(stdout);

        } catch (Exception e) {
            log.error("Python 执行异常: {}", e.getMessage(), e);
            return TaskResponse.exception(e.getMessage());
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * 自动检测系统 PATH 中可用的命令
     *
     * @param candidates 候选命令名列表（按优先级排序）
     * @return 第一个找到的命令名，如果都没找到返回 null
     */
    private String detectCommand(String[] candidates) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return null;
        }

        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String[] pathDirs = pathEnv.split(File.pathSeparator);

        for (String candidate : candidates) {
            for (String dir : pathDirs) {
                if (dir == null || dir.trim().isEmpty()) {
                    continue;
                }
                Path path = Paths.get(dir, candidate);
                if (Files.exists(path) && Files.isExecutable(path)) {
                    return candidate;
                }
                // Windows 上检查 .exe 后缀
                if (isWindows) {
                    Path exePath = Paths.get(dir, candidate + ".exe");
                    if (Files.exists(exePath) && Files.isExecutable(exePath)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 截断过长字符串
     */
    private String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "\n...[输出已截断，超出 "
                + maxLength + " 字符限制]";
    }

    /**
     * 清理临时工作目录
     */
    private void cleanupWorkDir(Path workDir) {
        if (workDir == null) {
            return;
        }
        try {
            Files.walk(workDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (Exception e) {
            log.warn("清理临时目录失败: {}", e.getMessage());
        }
    }
}
