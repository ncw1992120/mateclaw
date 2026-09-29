package vip.mate.dataagent.service.code.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import vip.mate.dataagent.service.code.PythonExecutionService;
import vip.mate.dataagent.service.code.PythonWorkerProperties;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class LocalProcessPythonExecutionService implements PythonExecutionService, AutoCloseable {
    private static final Set<String> TERMINAL = Set.of(
            "SUCCEEDED", "FAILED", "TIMEOUT", "CANCELLED", "OUTPUT_LIMIT", "RESULT_LIMIT",
            "OUTPUT_CONTRACT_ERROR", "RESULT_REF");
    private static final Set<String> ENV_ALLOWLIST = Set.of(
            "PATH", "HOME", "LANG", "LC_ALL", "TMPDIR", "TEMP", "TMP", "SYSTEMROOT", "WINDIR",
            "SYSTEMDRIVE", "PATHEXT", "COMSPEC");

    private final PythonWorkerProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, TaskState> tasks = new ConcurrentHashMap<>();
    private final Object admissionLock = new Object();
    private final Set<Process> activeProcesses = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public LocalProcessPythonExecutionService(PythonWorkerProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> submit(Map<String, Object> request) {
        if (request == null) throw new IllegalArgumentException("Python task request is required");
        String taskId = text(request.get("taskId"));
        if (taskId == null || !taskId.matches("[A-Za-z0-9._-]{1,128}")) {
            throw new IllegalArgumentException("invalid task id");
        }
        if (!properties.isEnabled()) return failed(taskId, "Python execution is disabled");
        String token = text(request.get("readToken"));

        synchronized (admissionLock) {
            if (closed) return failed(taskId, "Python worker service is shutting down");
            if (tasks.containsKey(taskId)) throw new IllegalStateException("task already exists");
            long activeCount = tasks.values().stream().filter(TaskState::isActive).count();
            if (activeCount >= Math.max(1, properties.getMaxConcurrentTasks())) {
                return failed(taskId, "maximum concurrent Python tasks reached");
            }

            TaskState state = new TaskState(taskId, token, stdoutLimit(request), stderrLimit());
            tasks.put(taskId, state);
            try {
                Path workerHome = resolveWorkerHome();
                Path taskDirectory = createTaskDirectory(taskId);
                state.taskDirectory = taskDirectory;
                Path requestFile = taskDirectory.resolve("request.json");
                Path responseFile = taskDirectory.resolve("response.json");
                objectMapper.writeValue(requestFile.toFile(), request);
                restrictFile(requestFile);

                ProcessBuilder processBuilder = new ProcessBuilder(
                        requirePythonCommand(), "-m", "runner.worker",
                        "--request", requestFile.toAbsolutePath().toString(),
                        "--response", responseFile.toAbsolutePath().toString());
                processBuilder.directory(workerHome.toFile());
                prepareEnvironment(processBuilder, workerHome);
                Process process = processBuilder.start();
                state.process = process;
                state.status = "RUNNING";
                activeProcesses.add(process);
                Thread.ofVirtual().name("python-worker-" + taskId).start(
                        () -> monitor(state, process, responseFile, timeoutSeconds(request)));
                return state.snapshot();
            } catch (Exception exception) {
                if (state.process != null) {
                    terminateTree(state.process);
                    activeProcesses.remove(state.process);
                }
                finish(state, "FAILED", null, safeMessage(exception, token), 1);
                cleanup(state.taskDirectory);
                return state.snapshot();
            }
        }
    }

    @Override
    public Map<String, Object> getStatus(String taskId) {
        TaskState state = tasks.get(taskId);
        if (state == null) throw new IllegalArgumentException("task not found");
        return state.snapshot();
    }

    @Override
    public Map<String, Object> cancel(String taskId) {
        TaskState state = tasks.get(taskId);
        if (state == null) throw new IllegalArgumentException("task not found");
        Process process = state.process;
        if (process == null || isTerminal(state.status)) return state.snapshot();
        finish(state, "CANCELLED", null, "task cancelled", safeExitValue(process));
        terminateTree(process);
        return state.snapshot();
    }

    private void monitor(TaskState state, Process process, Path responseFile, int timeoutSeconds) {
        AtomicBoolean outputExceeded = new AtomicBoolean();
        Thread stdoutReader = Thread.ofVirtual().name("python-worker-stdout-" + state.taskId).start(
                () -> collect(process.getInputStream(), state.stdout, state.maxStdoutBytes, outputExceeded, process));
        Thread stderrReader = Thread.ofVirtual().name("python-worker-stderr-" + state.taskId).start(
                () -> collect(process.getErrorStream(), state.stderr, state.maxStderrBytes, outputExceeded, process));
        String status;
        Map<String, Object> response = null;
        String error = null;
        int returnCode = -1;
        try {
            boolean exited = process.waitFor(Math.max(1, timeoutSeconds), java.util.concurrent.TimeUnit.SECONDS);
            if (!exited) {
                terminateTree(process);
                status = "TIMEOUT";
                error = "task timed out";
            } else {
                returnCode = process.exitValue();
                if (outputExceeded.get()) {
                    status = "OUTPUT_LIMIT";
                    error = "stdout or stderr exceeded configured byte limit";
                } else if (!Files.isRegularFile(responseFile)) {
                    status = "FAILED";
                    error = "Python Worker exited without a response file";
                } else {
                    try {
                        response = objectMapper.readValue(responseFile.toFile(), new TypeReference<>() {});
                        status = text(response.get("status"));
                        if (status == null || !TERMINAL.contains(status)) {
                            status = "FAILED";
                            error = "Python Worker returned an invalid task status";
                        } else if (returnCode != 0 && "SUCCEEDED".equals(status)) {
                            status = "FAILED";
                            error = "Python Worker exited with code " + returnCode;
                        }
                    } catch (Exception exception) {
                        status = "FAILED";
                        error = "invalid Python Worker response: " + safeMessage(exception, state.token);
                    }
                }
            }
            joinReader(stdoutReader);
            joinReader(stderrReader);
            if (outputExceeded.get() && !"TIMEOUT".equals(status)) {
                status = "OUTPUT_LIMIT";
                error = "stdout or stderr exceeded configured byte limit";
            }
            if (response != null) {
                Object responseOutput = response.get("output");
                if (state.stdout.size() == 0 && responseOutput != null) state.appendStdout(String.valueOf(responseOutput));
                if (error == null) error = text(response.get("error"));
            }
            if (error == null && "FAILED".equals(status) && state.stderr.size() > 0) error = state.stderrText();
            finish(state, status, response, error, returnCode);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            terminateTree(process);
            finish(state, "FAILED", null, "Python worker monitor interrupted", safeExitValue(process));
        } finally {
            activeProcesses.remove(process);
            cleanup(state.taskDirectory);
        }
    }

    private void collect(InputStream input, ByteArrayOutputStream destination, int limit,
                         AtomicBoolean exceeded, Process process) {
        byte[] buffer = new byte[8192];
        try (input) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                synchronized (destination) {
                    int remaining = Math.max(0, limit - destination.size());
                    if (remaining > 0) destination.write(buffer, 0, Math.min(read, remaining));
                    if (read > remaining && exceeded.compareAndSet(false, true)) terminateTree(process);
                }
            }
        } catch (IOException ignored) {
            // Process termination closes the pipe; the final status is decided by the monitor.
        }
    }

    private void prepareEnvironment(ProcessBuilder builder, Path workerHome) {
        Map<String, String> environment = builder.environment();
        Map<String, String> inherited = new LinkedHashMap<>(environment);
        environment.clear();
        inherited.forEach((key, value) -> {
            if (ENV_ALLOWLIST.contains(key.toUpperCase(java.util.Locale.ROOT))) environment.put(key, value);
        });
        Path sourceRoot = Files.isDirectory(workerHome.resolve("src")) ? workerHome.resolve("src") : workerHome;
        environment.put("PYTHONPATH", sourceRoot.toAbsolutePath().toString());
        environment.put("PYTHONIOENCODING", "utf-8");
        environment.put("PYTHONDONTWRITEBYTECODE", "1");
    }

    private Path resolveWorkerHome() {
        String configured = properties.getWorkerHome();
        if (configured != null && !configured.isBlank()) {
            Path resolved = Path.of(configured).toAbsolutePath().normalize();
            if (hasWorker(resolved)) return resolved;
            throw new IllegalStateException("Python Worker entry was not found under " + resolved);
        }
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        List<Path> candidates = List.of(
                current.resolve("python-worker"),
                current.resolve("mateclaw-python-runner"),
                current,
                current.getParent() == null ? current : current.getParent().resolve("mateclaw-python-runner"));
        return candidates.stream().filter(LocalProcessPythonExecutionService::hasWorker).findFirst()
                .orElseThrow(() -> new IllegalStateException("Python Worker entry not found; configure MATECLAW_PYTHON_WORKER_HOME"));
    }

    private static boolean hasWorker(Path home) {
        return Files.isRegularFile(home.resolve("src/runner/worker.py"))
                || Files.isRegularFile(home.resolve("runner/worker.py"));
    }

    private Path createTaskDirectory(String taskId) throws IOException {
        Path root = properties.getTempRoot().toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path taskDirectory = root.resolve(taskId).normalize();
        if (!taskDirectory.getParent().equals(root)) throw new IllegalArgumentException("invalid task directory");
        Files.createDirectory(taskDirectory);
        restrictDirectory(taskDirectory);
        return taskDirectory;
    }

    private static void restrictDirectory(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows ACLs inherit from the configured temp root.
        }
    }

    private static void restrictFile(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // The containing task directory remains private to the service account.
        }
    }

    private String requirePythonCommand() {
        String command = properties.getPythonCommand();
        if (command == null || command.isBlank()) throw new IllegalStateException("Python command is not configured");
        return command.trim();
    }

    private int timeoutSeconds(Map<String, Object> request) {
        Object limits = request.get("limits");
        if (limits instanceof Map<?, ?> values && values.get("timeout_seconds") instanceof Number number) {
            return Math.max(1, Math.min(900, number.intValue()));
        }
        return Math.max(1, properties.getDefaultTimeoutSeconds());
    }

    private int stdoutLimit(Map<String, Object> request) {
        Object limits = request.get("limits");
        if (limits instanceof Map<?, ?> values && values.get("max_stdout_bytes") instanceof Number number) {
            return Math.max(1, Math.min(5_000_000, number.intValue()));
        }
        return Math.max(1, properties.getMaxStdoutBytes());
    }

    private int stderrLimit() { return Math.max(1, properties.getMaxStderrBytes()); }

    private void terminateTree(Process process) {
        ProcessHandle root = process.toHandle();
        List<ProcessHandle> descendants = root.descendants().sorted(Comparator.comparingLong(ProcessHandle::pid).reversed()).toList();
        descendants.forEach(ProcessHandle::destroy);
        root.destroy();
        try {
            process.waitFor(Math.max(0, properties.getTerminationGraceMillis()), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        if (root.isAlive()) root.destroyForcibly();
    }

    private void finish(TaskState state, String status, Map<String, Object> response, String error, int returnCode) {
        synchronized (state) {
            if (isTerminal(state.status)) return;
            state.status = status;
            state.result = response == null ? null : response.get("result");
            state.outputRef = response == null ? null : response.get("outputRef");
            state.error = error;
            state.returnCode = returnCode;
            state.completedAt = System.currentTimeMillis();
        }
    }

    private static void joinReader(Thread reader) throws InterruptedException { reader.join(2_000); }

    private static int safeExitValue(Process process) {
        try { return process.exitValue(); } catch (IllegalThreadStateException ignored) { return -1; }
    }

    private static String safeMessage(Exception exception, String token) {
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        return token == null || token.isBlank() ? message : message.replace(token, "[REDACTED]");
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean isTerminal(String status) { return TERMINAL.contains(status); }

    private static Map<String, Object> failed(String taskId, String error) {
        return Map.of("taskId", taskId, "status", "FAILED", "error", error, "stats", Map.of("returncode", 1));
    }

    private static void cleanup(Path directory) {
        if (directory == null) return;
        try (var files = Files.walk(directory)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    @PreDestroy
    @Override
    public void close() {
        closed = true;
        for (Process process : activeProcesses) terminateTree(process);
    }

    private static final class TaskState {
        private final String taskId;
        private final String token;
        private final int maxStdoutBytes;
        private final int maxStderrBytes;
        private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        private volatile String status = "SUBMITTING";
        private volatile Process process;
        private volatile Path taskDirectory;
        private volatile Object result;
        private volatile Object outputRef;
        private volatile String error;
        private volatile int returnCode = -1;
        private volatile long completedAt;

        private TaskState(String taskId, String token, int maxStdoutBytes, int maxStderrBytes) {
            this.taskId = taskId;
            this.token = token;
            this.maxStdoutBytes = maxStdoutBytes;
            this.maxStderrBytes = maxStderrBytes;
        }

        private boolean isActive() { return !isTerminal(status); }

        private void appendStdout(String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            synchronized (stdout) {
                int remaining = Math.max(0, maxStdoutBytes - stdout.size());
                stdout.write(bytes, 0, Math.min(bytes.length, remaining));
            }
        }

        private String stdoutText() { return decode(stdout, maxStdoutBytes).replace(token == null ? "\u0000" : token, "[REDACTED]"); }
        private String stderrText() { return decode(stderr, maxStderrBytes).replace(token == null ? "\u0000" : token, "[REDACTED]"); }

        private Map<String, Object> snapshot() {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("taskId", taskId);
            snapshot.put("status", status);
            snapshot.put("output", stdoutText());
            snapshot.put("result", result);
            snapshot.put("outputRef", outputRef);
            snapshot.put("error", error == null ? null : error.replace(token == null ? "\u0000" : token, "[REDACTED]"));
            snapshot.put("stats", Map.of("returncode", returnCode));
            return snapshot;
        }

        private static String decode(ByteArrayOutputStream stream, int limit) {
            synchronized (stream) {
                byte[] bytes = stream.toByteArray();
                return new String(bytes, 0, Math.min(bytes.length, limit), StandardCharsets.UTF_8);
            }
        }
    }
}
