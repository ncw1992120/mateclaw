package vip.mate.dataagent.service.code;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vip.mate.dataagent.service.code.impl.LocalProcessPythonExecutionService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class LocalProcessPythonExecutionServiceTest {
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
        properties.setPythonCommand("python3");
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
        assertEquals("RUNNING", submitted.get("status"));

        Map<String, Object> completed = awaitStatus("success", status -> !"RUNNING".equals(status.get("status")));
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

        Map<String, Object> completed = awaitStatus("sleep", status -> !"RUNNING".equals(status.get("status")));
        assertEquals("TIMEOUT", completed.get("status"));
    }

    @Test
    void outputLimitTerminatesWorkerAndReturnsOutputLimit() throws Exception {
        Map<String, Object> request = request("overflow");
        ((Map<String, Object>) request.get("limits")).put("max_stdout_bytes", 128);
        service.submit(request);

        Map<String, Object> completed = awaitStatus("overflow", status -> !"RUNNING".equals(status.get("status")));
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

        assertEquals("FAILED", result.get("status"));
        assertTrue(String.valueOf(result.get("error")).contains("missing-python"));
    }

    @Test
    void missingOrCorruptResponseBecomesFailedInsteadOfRunningForever() throws Exception {
        for (String taskId : List.of("no-response", "bad-response")) {
            service.submit(request(taskId));
            Map<String, Object> completed = awaitStatus(taskId, status -> !"RUNNING".equals(status.get("status")));
            assertEquals("FAILED", completed.get("status"));
        }
    }

    @Test
    void enforcesMaximumConcurrentTasks() throws Exception {
        properties.setMaxConcurrentTasks(1);
        service.close();
        service = new LocalProcessPythonExecutionService(properties, new ObjectMapper());
        service.submit(request("sleep"));

        Map<String, Object> rejected = service.submit(request("second"));

        assertEquals("FAILED", rejected.get("status"));
        assertTrue(String.valueOf(rejected.get("error")).contains("concurrent"));
        service.cancel("sleep");
    }

    @Test
    void childReceivesOnlyAllowlistedEnvironment() throws Exception {
        service.submit(request("env"));
        Map<String, Object> completed = awaitStatus("env", status -> !"RUNNING".equals(status.get("status")));

        @SuppressWarnings("unchecked")
        List<String> names = new ObjectMapper().readValue((String) completed.get("output"), List.class);
        assertTrue(names.stream().allMatch(name -> List.of(
                "PATH", "HOME", "LANG", "LC_ALL", "TMPDIR", "TEMP", "TMP", "SYSTEMROOT", "WINDIR",
                "SYSTEMDRIVE", "PATHEXT", "PYTHONPATH", "PYTHONIOENCODING", "PYTHONDONTWRITEBYTECODE"
        ).contains(name)), names.toString());
    }

    private Map<String, Object> request(String taskId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("taskId", taskId);
        request.put("script", taskId);
        request.put("inputCatalog", Map.of());
        request.put("parameters", Map.of());
        request.put("limits", new LinkedHashMap<>(Map.of(
                "timeout_seconds", 5, "max_stdout_bytes", 4096, "max_result_bytes", 4096)));
        request.put("datasetReadEndpoint", "http://127.0.0.1:18089/dataagent/api/internal/read");
        request.put("datasetInputEndpoint", "http://127.0.0.1:18089/dataagent/api/internal/input");
        request.put("preferPreparedInputs", true);
        request.put("readToken", "short-lived-task-token");
        return request;
    }

    private Map<String, Object> awaitStatus(String taskId, Predicate<Map<String, Object>> done) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        Map<String, Object> status;
        do {
            status = service.getStatus(taskId);
            if (done.test(status)) return status;
            Thread.sleep(25);
        } while (System.nanoTime() < deadline);
        fail("task did not reach a terminal status: " + taskId + " " + service.getStatus(taskId));
        return Map.of();
    }

    private void writeWorker(String source) throws Exception {
        Path worker = tempDir.resolve("worker/src/runner/worker.py");
        Files.createDirectories(worker.getParent());
        Files.writeString(worker, source);
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
