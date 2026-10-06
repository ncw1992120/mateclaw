package vip.mate.dataagent.service.code.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import vip.mate.dataagent.service.code.PythonWorkerCompletedEvent;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class LocalProcessPythonExecutionService implements PythonExecutionService, AutoCloseable {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(LocalProcessPythonExecutionService.class);
    private static final String DEPENDENCY_PROBE = "import importlib.util,sys; required=('pandas','polars','pydantic','pyarrow'); missing=[name for name in required if importlib.util.find_spec(name) is None]; print(','.join(missing)); sys.exit(bool(missing))";
    private static final List<String> WORKER_DEPENDENCIES = List.of("pandas", "polars", "pydantic", "pyarrow");
    private static final Set<String> TERMINAL = Set.of(
            "SUCCEEDED", "FAILED", "TIMEOUT", "CANCELLED", "OUTPUT_LIMIT", "RESULT_LIMIT",
            "OUTPUT_CONTRACT_ERROR", "RESULT_REF");
    private static final Set<String> ENV_ALLOWLIST = Set.of(
            "PATH", "HOME", "LANG", "LC_ALL", "TMPDIR", "TEMP", "TMP", "SYSTEMROOT", "WINDIR",
            "SYSTEMDRIVE", "PATHEXT", "COMSPEC");

    private final PythonWorkerProperties properties;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, TaskState> tasks = new ConcurrentHashMap<>();
    private final Set<Process> activeProcesses = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService completedTaskCleanup;
    private final ThreadPoolExecutor workerExecutor;
    private volatile boolean closed;

    @Autowired
    public LocalProcessPythonExecutionService(PythonWorkerProperties properties, ObjectMapper objectMapper,
                                             ApplicationEventPublisher eventPublisher) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        int maxConcurrentTasks = Math.max(1, properties.getMaxConcurrentTasks());
        ThreadFactory workerThreadFactory = runnable -> {
            Thread thread = new Thread(runnable, "python-worker-launch");
            thread.setDaemon(true);
            return thread;
        };
        this.workerExecutor = new ThreadPoolExecutor(maxConcurrentTasks, maxConcurrentTasks,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(1, properties.getMaxQueuedTasks())),
                workerThreadFactory, new ThreadPoolExecutor.AbortPolicy());
        this.completedTaskCleanup = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "python-worker-task-cleanup");
            thread.setDaemon(true);
            return thread;
        });
        long cleanupPeriodMillis = Math.max(10L,
                Math.min(1_000L, Math.max(1L, properties.getCompletedTaskTtlMillis())));
        this.completedTaskCleanup.scheduleWithFixedDelay(this::scheduledPruneCompletedTasks,
                cleanupPeriodMillis, cleanupPeriodMillis, TimeUnit.MILLISECONDS);
    }

    public LocalProcessPythonExecutionService(PythonWorkerProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, event -> { });
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

        if (closed) return failed(taskId, "Python worker service is shutting down");
        pruneCompletedTasks();
        TaskState state = new TaskState(taskId, token, stdoutLimit(request), stderrLimit());
        if (tasks.putIfAbsent(taskId, state) != null) throw new IllegalStateException("task already exists");
        try {
            workerExecutor.execute(() -> runTask(state, request));
        } catch (RejectedExecutionException exception) {
            finish(state, "FAILED", null, "Python task queue is full", 1);
        }
        return state.snapshot();
    }

    private void runTask(TaskState state, Map<String, Object> request) {
        if (isTerminal(state.status)) return;
        try {
            Path workerHome = resolveWorkerHome();
            String pythonCommand = requirePythonCommand(workerHome);
            Path taskDirectory = createTaskDirectory(state.taskId);
            state.taskDirectory = taskDirectory;
            Path requestFile = taskDirectory.resolve("request.json");
            Path responseFile = taskDirectory.resolve("response.json");
            objectMapper.writeValue(requestFile.toFile(), request);
            restrictFile(requestFile);

            ProcessBuilder processBuilder = new ProcessBuilder(
                    pythonCommand, "-m", "runner.worker",
                    "--request", requestFile.toAbsolutePath().toString(),
                    "--response", responseFile.toAbsolutePath().toString());
            processBuilder.directory(workerHome.toFile());
            prepareEnvironment(processBuilder, workerHome);
            Process process = processBuilder.start();
            synchronized (state) {
                if (isTerminal(state.status)) {
                    terminateTree(process);
                    cleanup(taskDirectory);
                    return;
                }
                state.process = process;
                state.status = "RUNNING";
                activeProcesses.add(process);
            }
            monitor(state, process, responseFile, timeoutSeconds(request));
        } catch (Exception exception) {
            Process process = state.process;
            if (process != null) {
                terminateTree(process);
                activeProcesses.remove(process);
            }
            finish(state, "FAILED", null, safeMessage(exception, state.token), 1);
            cleanup(state.taskDirectory);
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
        if (isTerminal(state.status)) return state.snapshot();
        if (process == null) {
            finish(state, "CANCELLED", null, "task cancelled", 1);
            cleanup(state.taskDirectory);
            return state.snapshot();
        }
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
                current.resolve("mateclaw-dataagent/python-worker"),
                current.resolve("mateclaw-dataagent").resolve("python-worker"),
                current);
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

    private String requirePythonCommand(Path workerHome) {
        String configured = properties.getPythonCommand();
        if (configured != null && !configured.isBlank()) {
            String command = configured.trim();
            String failure = inspectPython(command, workerHome);
            if (failure == null) return command;
            throw pythonUnavailable(command, failure, workerHome);
        }

        Path virtualEnvironmentPython = virtualEnvironmentPython(workerHome, System.getProperty("os.name", ""));
        List<String> diagnostics = new ArrayList<>();
        if (virtualEnvironmentPython != null) {
            String command = virtualEnvironmentPython.toString();
            String failure = inspectPython(command, workerHome);
            if (failure == null) return command;
            diagnostics.add(command + ": " + failure);
        }

        List<String> systemCandidates = isWindows()
                ? List.of("python.exe", "python")
                : List.of("python3", "python");
        for (String command : systemCandidates) {
            String failure = inspectPython(command, workerHome);
            if (failure == null) return command;
            diagnostics.add(command + ": " + failure);
        }
        String selectedFailure = diagnostics.isEmpty()
                ? "no Python interpreter candidate was found"
                : String.join("; ", diagnostics);
        throw pythonUnavailable(virtualEnvironmentPython == null ? systemCandidates.get(0)
                : virtualEnvironmentPython.toString(), selectedFailure, workerHome);
    }

    private String inspectPython(String command, Path workerHome) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command, "-c", DEPENDENCY_PROBE)
                    .directory(workerHome.toFile())
                    .redirectErrorStream(true);
            prepareEnvironment(builder, workerHome);
            process = builder.start();
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                terminateTree(process);
                return "dependency check timed out";
            }
            String output = new String(process.getInputStream().readNBytes(4096), StandardCharsets.UTF_8).trim();
            if (process.exitValue() == 0) return null;
            String missing = output.isBlank() ? "dependency check failed" : "missing dependencies: " + output;
            return missing;
        } catch (IOException exception) {
            return "cannot start interpreter: " + safeMessage(exception, null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (process != null) terminateTree(process);
            return "dependency check interrupted";
        }
    }

    private IllegalStateException pythonUnavailable(String command, String reason, Path workerHome) {
        return new IllegalStateException("Python Worker cannot use interpreter '" + command + "': " + reason
                + ". Required modules: " + String.join(", ", WORKER_DEPENDENCIES)
                + ". Install the locked dependencies offline with: uv sync --locked --directory \""
                + workerHome + "\".");
    }

    private static Path virtualEnvironmentPython(Path workerHome, String osName) {
        boolean windows = osName.toLowerCase(java.util.Locale.ROOT).contains("win");
        Path candidate = windows
                ? workerHome.resolve(".venv/Scripts/python.exe")
                : workerHome.resolve(".venv/bin/python");
        return Files.isRegularFile(candidate) && (windows || Files.isExecutable(candidate)) ? candidate : null;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
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
        Map<String, Object> snapshot;
        synchronized (state) {
            if (isTerminal(state.status)) return;
            state.status = status;
            state.result = response == null ? null : response.get("result");
            state.outputRef = response == null ? null : response.get("outputRef");
            state.error = error;
            state.returnCode = returnCode;
            state.completedAt = System.currentTimeMillis();
            snapshot = state.snapshot();
        }
        try {
            // Spring's default event multicaster is synchronous: the database listener finishes
            // before this task is eligible for cache eviction.
            eventPublisher.publishEvent(new PythonWorkerCompletedEvent(snapshot));
            state.persisted = true;
        } catch (RuntimeException exception) {
            org.slf4j.LoggerFactory.getLogger(LocalProcessPythonExecutionService.class)
                    .error("Could not persist terminal Python Worker snapshot for task {}", state.taskId, exception);
        }
        pruneCompletedTasks();
    }

    private void pruneCompletedTasks() {
        long now = System.currentTimeMillis();
        long ttlMillis = Math.max(0, properties.getCompletedTaskTtlMillis());
        List<TaskState> completed = tasks.values().stream()
                .filter(state -> state.persisted && isTerminal(state.status))
                .sorted(Comparator.comparingLong(state -> state.completedAt))
                .toList();
        for (TaskState state : completed) {
            if (now - state.completedAt >= ttlMillis) tasks.remove(state.taskId, state);
        }

        completed = tasks.values().stream()
                .filter(state -> state.persisted && isTerminal(state.status))
                .sorted(Comparator.comparingLong(state -> state.completedAt))
                .toList();
        int excess = completed.size() - Math.max(0, properties.getMaxCompletedTasks());
        for (int i = 0; i < excess; i++) tasks.remove(completed.get(i).taskId, completed.get(i));
    }

    private void scheduledPruneCompletedTasks() {
        try {
            pruneCompletedTasks();
        } catch (RuntimeException exception) {
            // A failed sweep must not suppress all future scheduled executions.
            LOGGER.error("Could not prune completed Python Worker tasks", exception);
        }
    }

    private static void joinReader(Thread reader) throws InterruptedException { reader.join(2_000); }

    private static int safeExitValue(Process process) {
        if (process == null) return -1;
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
        completedTaskCleanup.shutdownNow();
        workerExecutor.shutdownNow();
        for (TaskState state : tasks.values()) {
            if (!isTerminal(state.status)) {
                Process process = state.process;
                finish(state, "CANCELLED", null, "Python worker service is shutting down", safeExitValue(process));
                if (process != null) terminateTree(process);
                cleanup(state.taskDirectory);
            }
        }
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
        private volatile boolean persisted;

        private TaskState(String taskId, String token, int maxStdoutBytes, int maxStderrBytes) {
            this.taskId = taskId;
            this.token = token;
            this.maxStdoutBytes = maxStdoutBytes;
            this.maxStderrBytes = maxStderrBytes;
        }

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
