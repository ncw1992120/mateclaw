package vip.mate.dataagent.service.code;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vip.mate.dataagent.service.code.PythonWorkerCompletedEvent;
import vip.mate.dataagent.service.code.impl.LocalProcessPythonExecutionService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class LocalProcessPythonExecutionServiceTest {
    @Test
    void workerQueueAndCompletedTaskRetentionHaveSafeDefaults() {
        PythonWorkerProperties defaults = new PythonWorkerProperties();
        assertEquals(32, defaults.getMaxQueuedTasks());
        assertEquals(300_000L, defaults.getCompletedTaskTtlMillis());
    }

    @Test
    void submitIsRejectedWhenPythonExecutorIsDisabled() throws Exception {
        PythonWorkerProperties properties = new PythonWorkerProperties();
        properties.setEnabled(false);
        properties.setWorkerHome(tempDir.resolve("worker").toString());
        properties.setPythonCommand("python3");
        LocalProcessPythonExecutionService service = new LocalProcessPythonExecutionService(properties, new ObjectMapper());

        Map<String, Object> status = service.submit(Map.of("taskId", "disabled-task"));

        assertEquals("FAILED", status.get("status"));
        assertTrue(status.get("error").toString().contains("disabled"));
    }

    @TempDir Path tempDir;

    private PythonWorkerProperties properties;
    private LocalProcessPythonExecutionService service;

    @BeforeEach
    void setUp() throws Exception {
        properties = new PythonWorkerProperties();
        Path testPython = tempDir.resolve("test-python");
        writePythonCommand(testPython, "test-python", false);
        properties.setPythonCommand(testPython.toString());
        properties.setWorkerHome(tempDir.resolve("worker").toString());
        properties.setTempRoot(tempDir.resolve("tasks"));
        properties.setMaxConcurrentTasks(2);
        writeWorker("""
                import argparse, json, os, subprocess, sys, time
                from pathlib import Path
                parser = argparse.ArgumentParser()
                parser.add_argument('--request', required=True)
                parser.add_argument('--response', required=True)
                args = parser.parse_args()
                request = json.loads(Path(args.request).read_text(encoding='utf-8'))
                mode = request['script']
                if mode == 'sleep':
                    print('started', flush=True)
                    time.sleep(30)
                elif mode == 'overflow':
                    print('x' * 100000, flush=True)
                    time.sleep(1)
                elif mode == 'tree':
                    child = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(30)'])
                    Path(args.response).with_name('child.pid').write_text(str(child.pid))
                    print('child-started', flush=True)
                    time.sleep(30)
                elif mode == 'env':
                    response = {'taskId': request['taskId'], 'status': 'SUCCEEDED', 'output': json.dumps(sorted(os.environ)), 'result': None, 'outputRef': None, 'error': None, 'stats': {'returncode': 0}}
                    Path(args.response).write_text(json.dumps(response), encoding='utf-8')
                elif mode not in ('no-response', 'bad-response'):
                    response = {'taskId': request['taskId'], 'status': 'SUCCEEDED', 'output': 'worker log', 'result': {'schemaVersion': '1.0', 'kind': 'scalar', 'data': {'value': 9, 'dataType': 'number'}, 'meta': {'rowCount': 0, 'truncated': False, 'sourceInputs': []}}, 'outputRef': None, 'error': None, 'stats': {'returncode': 0}}
                    Path(args.response).write_text(json.dumps(response), encoding='utf-8')
                elif mode == 'bad-response':
                    Path(args.response).write_text('{broken', encoding='utf-8')
                """);
        service = new LocalProcessPythonExecutionService(properties, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        if (service != null) service.close();
    }

    @Test
    void submitTracksWorkerStatusAndResult() throws Exception {
        Map<String, Object> submitted = service.submit(request("success"));
        assertAccepted(submitted);

        Map<String, Object> completed = awaitStatus("success", LocalProcessPythonExecutionServiceTest::isTerminal);
        assertEquals("SUCCEEDED", completed.get("status"));
        assertEquals("worker log", completed.get("output"));
        assertEquals(9, ((Map<?, ?>) completed.get("result")).get("data") instanceof Map<?, ?> data
                ? data.get("value") : null);
    }

    @Test
    void rejectsUnknownTaskStatus() {
        assertThrows(IllegalArgumentException.class, () -> service.getStatus("unknown-task"));
    }

    @Test
    void timeoutBecomesTerminalTimeoutState() throws Exception {
        Map<String, Object> request = request("sleep");
        ((Map<String, Object>) request.get("limits")).put("timeout_seconds", 1);
        service.submit(request);

        Map<String, Object> completed = awaitStatus("sleep", LocalProcessPythonExecutionServiceTest::isTerminal);
        assertEquals("TIMEOUT", completed.get("status"));
    }

    @Test
    void completedTaskExpiresWhileServiceIsIdle() throws Exception {
        service.close();
        properties.setCompletedTaskTtlMillis(100);
        service = new LocalProcessPythonExecutionService(properties, new ObjectMapper());

        service.submit(request("idle-expiry"));
        awaitStatus("idle-expiry", status -> "SUCCEEDED".equals(status.get("status")));

        await(() -> {
            try {
                service.getStatus("idle-expiry");
                return false;
            } catch (IllegalArgumentException expected) {
                return true;
            }
        });
    }

    @Test
    void runningTaskIsRetainedPastCompletedTaskTtl() throws Exception {
        service.close();
        properties.setCompletedTaskTtlMillis(50);
        service = new LocalProcessPythonExecutionService(properties, new ObjectMapper());

        service.submit(request("sleep"));
        await(() -> "RUNNING".equals(service.getStatus("sleep").get("status")));
        Thread.sleep(250);

        assertEquals("RUNNING", service.getStatus("sleep").get("status"));
    }

    @Test
    void outputLimitTerminatesWorkerAndReturnsOutputLimit() throws Exception {
        Map<String, Object> request = request("overflow");
        ((Map<String, Object>) request.get("limits")).put("max_stdout_bytes", 128);
        service.submit(request);

        Map<String, Object> completed = awaitStatus("overflow", LocalProcessPythonExecutionServiceTest::isTerminal);
        assertEquals("OUTPUT_LIMIT", completed.get("status"));
        assertTrue(((String) completed.get("output")).getBytes().length <= 128);
    }

    @Test
    void cancelTerminatesWorkerAndItsDescendants() throws Exception {
        service.submit(request("tree"));
        Path childPidFile = tempDir.resolve("tasks");
        awaitFile(childPidFile, "tree/child.pid");
        long childPid = Long.parseLong(Files.readString(childPidFile.resolve("tree/child.pid")));

        Map<String, Object> cancelled = service.cancel("tree");

        assertEquals("CANCELLED", cancelled.get("status"));
        await(() -> ProcessHandle.of(childPid).map(handle -> !handle.isAlive()).orElse(true));
    }

    @Test
    void unavailablePythonProducesFailedTask() throws Exception {
        service.close();
        properties.setPythonCommand(tempDir.resolve("missing-python").toString());
        service = new LocalProcessPythonExecutionService(properties, new ObjectMapper());

        Map<String, Object> result = service.submit(request("success"));

        Map<String, Object> completed = awaitStatus("success", LocalProcessPythonExecutionServiceTest::isTerminal);
        assertEquals("FAILED", completed.get("status"));
        assertTrue(String.valueOf(completed.get("error")).contains("missing-python"));
    }

    @Test
    void discoversWorkerFromDataAgentAndRepositoryWorkingDirectories() throws Exception {
        Path repository = tempDir.resolve("repository");
        Path dataAgent = repository.resolve("mateclaw-dataagent");
        Path workerHome = dataAgent.resolve("python-worker");
        Path marker = writePythonCommand(workerHome.resolve(".venv/bin/python"), "managed-python", false);
        writeWorkerAt(workerHome);
        properties.setWorkerHome(null);
        properties.setPythonCommand(null);
        service = new LocalProcessPythonExecutionService(properties, new ObjectMapper());

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", dataAgent.toString());
            assertAccepted(service.submit(request("module-root")));
            assertEquals("SUCCEEDED", awaitStatus("module-root", status -> "SUCCEEDED".equals(status.get("status"))).get("status"));

            System.setProperty("user.dir", repository.toString());
            assertAccepted(service.submit(request("repository-root")));
            assertEquals("SUCCEEDED", awaitStatus("repository-root", status -> "SUCCEEDED".equals(status.get("status"))).get("status"));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
        assertEquals(List.of("managed-python", "managed-python", "managed-python", "managed-python"), Files.readAllLines(marker));
    }

    @Test
    void explicitWorkerHomeAndPythonCommandTakePriority() throws Exception {
        Path repository = tempDir.resolve("repository");
        Path discoveredWorker = repository.resolve("mateclaw-dataagent/python-worker");
        Path discoveredMarker = writePythonCommand(discoveredWorker.resolve(".venv/bin/python"), "venv-python", false);
        writeWorkerAt(discoveredWorker);
        Path explicitWorker = tempDir.resolve("explicit-worker");
        writeWorkerAt(explicitWorker);
        Path explicitCommand = tempDir.resolve("explicit-python");
        Path explicitMarker = writePythonCommand(explicitCommand, "explicit-python", false);
        properties.setWorkerHome(explicitWorker.toString());
        properties.setPythonCommand(explicitCommand.toString());
        service = new LocalProcessPythonExecutionService(properties, new ObjectMapper());

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", repository.toString());
            service.submit(request("explicit-priority"));
            assertEquals("SUCCEEDED", awaitStatus("explicit-priority", status -> "SUCCEEDED".equals(status.get("status"))).get("status"));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
        assertEquals(List.of("explicit-python", "explicit-python"), Files.readAllLines(explicitMarker));
        assertFalse(Files.exists(discoveredMarker));
    }

    @Test
    void discoversWindowsAndPosixVirtualEnvironmentLayouts() throws Exception {
        Path workerHome = tempDir.resolve("platform-worker");
        writeWorkerAt(workerHome);
        Path posixMarker = writePythonCommand(workerHome.resolve(".venv/bin/python"), "posix-python", false);
        properties.setWorkerHome(workerHome.toString());
        properties.setPythonCommand(null);
        service = new LocalProcessPythonExecutionService(properties, new ObjectMapper());

        service.submit(request("posix-venv"));
        assertEquals("SUCCEEDED", awaitStatus("posix-venv", status -> "SUCCEEDED".equals(status.get("status"))).get("status"));
        assertEquals(List.of("posix-python", "posix-python"), Files.readAllLines(posixMarker));

        service.close();
        Path windowsMarker = writePythonCommand(workerHome.resolve(".venv/Scripts/python.exe"), "windows-python", false);
        service = new LocalProcessPythonExecutionService(properties, new ObjectMapper());
        String originalOsName = System.getProperty("os.name");
        try {
            System.setProperty("os.name", "Windows 10");
            service.submit(request("windows-venv"));
            assertEquals("SUCCEEDED", awaitStatus("windows-venv", status -> "SUCCEEDED".equals(status.get("status"))).get("status"));
        } finally {
            System.setProperty("os.name", originalOsName);
        }
        assertEquals(List.of("windows-python", "windows-python"), Files.readAllLines(windowsMarker));
    }

    @Test
    void reportsMissingWorkerDependenciesAndOfflineRepairCommand() throws Exception {
        Path python = tempDir.resolve("missing-dependencies-python");
        writePythonCommand(python, "", true);
        properties.setPythonCommand(python.toString());
        service = new LocalProcessPythonExecutionService(properties, new ObjectMapper());

        service.submit(request("missing-dependencies"));
        Map<String, Object> result = awaitStatus("missing-dependencies", LocalProcessPythonExecutionServiceTest::isTerminal);

        String error = String.valueOf(result.get("error"));
        assertEquals("FAILED", result.get("status"));
        assertTrue(error.contains("missing-dependencies-python"), error);
        assertTrue(error.contains("polars"), error);
        assertTrue(error.contains("pyarrow"), error);
        assertTrue(error.contains("uv sync --locked"), error);
    }

    @Test
    void terminalCacheEvictsOldEntriesButNeverAnActiveTask() throws Exception {
        properties.setMaxCompletedTasks(1);
        properties.setCompletedTaskTtlMillis(1_800_000L);
        service = new LocalProcessPythonExecutionService(properties, new ObjectMapper());
        Map<String, Object> activeRequest = request("active-task");
        activeRequest.put("script", "sleep");
        ((Map<String, Object>) activeRequest.get("limits")).put("timeout_seconds", 30);
        service.submit(activeRequest);
        service.submit(request("terminal-old"));
        awaitStatus("terminal-old", status -> "SUCCEEDED".equals(status.get("status")));
        service.submit(request("terminal-new"));
        awaitStatus("terminal-new", status -> "SUCCEEDED".equals(status.get("status")));

        assertEquals("RUNNING", service.getStatus("active-task").get("status"));
        assertThrows(IllegalArgumentException.class, () -> service.getStatus("terminal-old"));
        assertEquals("SUCCEEDED", service.getStatus("terminal-new").get("status"));
        service.cancel("active-task");
    }

    @Test
    void terminalCacheExpiresCompletedTasksAfterConfiguredTtl() throws Exception {
        properties.setCompletedTaskTtlMillis(0L);
        service = new LocalProcessPythonExecutionService(properties, new ObjectMapper());

        service.submit(request("expired-terminal"));
        await(() -> {
            try {
                service.getStatus("expired-terminal");
                return false;
            } catch (IllegalArgumentException expected) {
                return true;
            }
        });

        assertThrows(IllegalArgumentException.class, () -> service.getStatus("expired-terminal"));
    }

    @Test
    void publishesTerminalSnapshotForSynchronousPersistence() throws Exception {
        List<PythonWorkerCompletedEvent> events = new CopyOnWriteArrayList<>();
        service = new LocalProcessPythonExecutionService(properties, new ObjectMapper(),
                event -> events.add((PythonWorkerCompletedEvent) event));

        service.submit(request("event-task"));
        awaitStatus("event-task", status -> "SUCCEEDED".equals(status.get("status")));

        assertEquals(1, events.size());
        assertEquals("event-task", events.getFirst().taskSnapshot().get("taskId"));
        assertEquals("SUCCEEDED", events.getFirst().taskSnapshot().get("status"));
        assertEquals("worker log", events.getFirst().taskSnapshot().get("output"));
        assertNotNull(events.getFirst().taskSnapshot().get("result"));
    }

    @Test
    void queuesTasksAboveTheConcurrencyLimitAndRunsThemWhenCapacityIsFreed() throws Exception {
        for (String taskId : List.of("active-one", "active-two")) {
            Map<String, Object> activeRequest = request("sleep");
            activeRequest.put("taskId", taskId);
            ((Map<String, Object>) activeRequest.get("limits")).put("timeout_seconds", 30);
            assertAccepted(service.submit(activeRequest));
        }

        Map<String, Object> queuedRequest = request("success");
        queuedRequest.put("taskId", "third-worker");
        Map<String, Object> queued = service.submit(queuedRequest);

        assertTrue(List.of("SUBMITTING", "RUNNING").contains(queued.get("status")));
        await(() -> "RUNNING".equals(service.getStatus("active-one").get("status"))
                && "RUNNING".equals(service.getStatus("active-two").get("status")));

        service.cancel("active-one");
        Map<String, Object> completed = awaitStatus("third-worker", status -> "SUCCEEDED".equals(status.get("status")));
        assertEquals("SUCCEEDED", completed.get("status"));
        assertEquals("RUNNING", service.getStatus("active-two").get("status"));
    }

    @Test
    void missingOrCorruptResponseBecomesFailedInsteadOfRunningForever() throws Exception {
        for (String taskId : List.of("no-response", "bad-response")) {
            service.submit(request(taskId));
            Map<String, Object> completed = awaitStatus(taskId, LocalProcessPythonExecutionServiceTest::isTerminal);
            assertEquals("FAILED", completed.get("status"));
        }
    }

    @Test
    void queuesAdditionalTasksWhenConcurrencyLimitIsReached() throws Exception {
        properties.setMaxConcurrentTasks(1);
        service.close();
        service = new LocalProcessPythonExecutionService(properties, new ObjectMapper());
        service.submit(request("sleep"));

        Map<String, Object> queuedRequest = request("success");
        queuedRequest.put("taskId", "second");
        Map<String, Object> queued = service.submit(queuedRequest);

        assertTrue(List.of("SUBMITTING", "RUNNING").contains(queued.get("status")));
        service.cancel("sleep");
        assertEquals("SUCCEEDED", awaitStatus("second", status -> "SUCCEEDED".equals(status.get("status"))).get("status"));
    }

    @Test
    void rejectsOnlyWhenTheBoundedQueueIsFull() throws Exception {
        properties.setMaxConcurrentTasks(1);
        properties.setMaxQueuedTasks(1);
        service.close();
        service = new LocalProcessPythonExecutionService(properties, new ObjectMapper());

        service.submit(request("sleep"));
        Map<String, Object> queuedRequest = request("success");
        queuedRequest.put("taskId", "queued-one");
        assertAccepted(service.submit(queuedRequest));

        Map<String, Object> rejectedRequest = request("success");
        rejectedRequest.put("taskId", "queue-full");
        Map<String, Object> rejected = service.submit(rejectedRequest);
        assertEquals("FAILED", rejected.get("status"));
        assertTrue(String.valueOf(rejected.get("error")).contains("queue is full"));

        service.cancel("sleep");
        assertEquals("SUCCEEDED", awaitStatus("queued-one", status -> "SUCCEEDED".equals(status.get("status"))).get("status"));
    }

    @Test
    void childReceivesOnlyAllowlistedEnvironment() throws Exception {
        service.submit(request("env"));
        Map<String, Object> completed = awaitStatus("env", LocalProcessPythonExecutionServiceTest::isTerminal);

        @SuppressWarnings("unchecked")
        List<String> names = new ObjectMapper().readValue((String) completed.get("output"), List.class);
        java.util.Set<String> allowed = new java.util.HashSet<>(List.of(
                "PATH", "HOME", "LANG", "LC_ALL", "TMPDIR", "TEMP", "TMP", "SYSTEMROOT", "WINDIR",
                "SYSTEMDRIVE", "PATHEXT", "PWD", "SHLVL", "PYTHONPATH", "PYTHONIOENCODING", "PYTHONDONTWRITEBYTECODE"
        ));
        if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            allowed.add("__CF_USER_TEXT_ENCODING");
        }
        assertTrue(names.stream().allMatch(allowed::contains), names.toString());
    }

    private Map<String, Object> request(String taskId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("taskId", taskId);
        request.put("script", taskId);
        request.put("inputCatalog", Map.of());
        request.put("parameters", Map.of());
        request.put("limits", new LinkedHashMap<>(Map.of(
                "timeout_seconds", 30, "max_stdout_bytes", 4096, "max_result_bytes", 4096)));
        request.put("datasetReadEndpoint", "http://127.0.0.1:18089/dataagent/api/internal/read");
        request.put("datasetInputEndpoint", "http://127.0.0.1:18089/dataagent/api/internal/input");
        request.put("preferPreparedInputs", true);
        request.put("readToken", "short-lived-task-token");
        return request;
    }

    private Map<String, Object> awaitStatus(String taskId, Predicate<Map<String, Object>> done) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        Map<String, Object> status;
        do {
            status = service.getStatus(taskId);
            if (done.test(status)) return status;
            Thread.sleep(25);
        } while (System.nanoTime() < deadline);
        fail("task did not reach a terminal status: " + taskId + " " + service.getStatus(taskId));
        return Map.of();
    }

    private static boolean isTerminal(Map<String, Object> status) {
        return List.of("SUCCEEDED", "FAILED", "TIMEOUT", "CANCELLED", "OUTPUT_LIMIT", "RESULT_LIMIT",
                "OUTPUT_CONTRACT_ERROR", "RESULT_REF").contains(status.get("status"));
    }

    private static void assertAccepted(Map<String, Object> status) {
        assertTrue(List.of("SUBMITTING", "RUNNING", "SUCCEEDED").contains(status.get("status")), status.toString());
    }

    private void writeWorker(String source) throws Exception {
        writeWorkerAt(tempDir.resolve("worker"), source);
    }

    private static void writeWorkerAt(Path workerHome) throws Exception {
        writeWorkerAt(workerHome, """
                import argparse, json
                from pathlib import Path
                parser = argparse.ArgumentParser()
                parser.add_argument('--request', required=True)
                parser.add_argument('--response', required=True)
                args = parser.parse_args()
                request = json.loads(Path(args.request).read_text(encoding='utf-8'))
                response = {'taskId': request['taskId'], 'status': 'SUCCEEDED', 'output': 'worker log', 'result': {'schemaVersion': '1.0', 'kind': 'scalar', 'data': {'value': 9, 'dataType': 'number'}, 'meta': {'rowCount': 0, 'truncated': False, 'sourceInputs': []}}, 'outputRef': None, 'error': None, 'stats': {'returncode': 0}}
                Path(args.response).write_text(json.dumps(response), encoding='utf-8')
                """);
    }

    private static void writeWorkerAt(Path workerHome, String source) throws Exception {
        Path worker = workerHome.resolve("src/runner/worker.py");
        Files.createDirectories(worker.getParent());
        Files.writeString(worker, source);
    }

    private Path writePythonCommand(Path command, String marker, boolean missingDependencies) throws Exception {
        Files.createDirectories(command.getParent());
        Path markerFile = command.resolveSibling(command.getFileName() + ".invocations");
        String quote = "'" + markerFile.toString().replace("'", "'\\''") + "'";
        String probe = missingDependencies
                ? "printf '%s\\n' 'polars,pyarrow'; exit 1"
                : "exit 0";
        String script = "#!/bin/sh\nprintf '%%s\\n' '%s' >> %s\n"
                + "if [ \"$1\" = \"-c\" ]; then %s; fi\n"
                + "exec python3 \"$@\"\n";
        Files.writeString(command, script.formatted(marker, quote, probe));
        try {
            Files.setPosixFilePermissions(command, java.util.EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE));
        } catch (UnsupportedOperationException ignored) {
            command.toFile().setExecutable(true);
        }
        return markerFile;
    }

    private static void awaitFile(Path root, String relativePath) throws Exception {
        await(() -> Files.exists(root.resolve(relativePath)));
    }

    private static void await(CheckedBoolean condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.get()) return;
            Thread.sleep(20);
        }
        fail("condition was not met before timeout");
    }

    @FunctionalInterface
    private interface CheckedBoolean { boolean get() throws Exception; }
}
