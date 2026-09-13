package vip.mate.dataagent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import vip.mate.dataagent.service.code.CodeExecutorProperties;
import vip.mate.sdk.service.MateClawRuntime;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies the legacy Agent tool channel, not only LocalCodeExecutorService in isolation.
 * Set MATECLAW_LEGACY_REAL_PYTHON=true in a Python-enabled acceptance container to run
 * the same callback through a real Python interpreter.
 */
class PythonAnalysisToolIntegrationTest {

    @Test
    void registersPythonAnalysisAndExecutesThroughTheToolCallback() throws Exception {
        boolean realPython = "true".equalsIgnoreCase(System.getenv("MATECLAW_LEGACY_REAL_PYTHON"));
        CodeExecutorProperties properties = new CodeExecutorProperties();
        properties.setPythonCommand(realPython ? "python3" : "/bin/sh");
        properties.setPipCommand("/bin/false");
        properties.setPipTimeoutSeconds(1);
        properties.setCodeTimeoutSeconds(5);
        properties.setMaxRetries(1);

        MateClawRuntime runtime = mock(MateClawRuntime.class);
        AtomicReference<ToolCallback> callback = new AtomicReference<>();
        doAnswer(invocation -> {
            callback.set(invocation.getArgument(0));
            return null;
        }).when(runtime).registerTool(any(ToolCallback.class));

        PythonAnalysisTool tool = new PythonAnalysisTool(runtime, properties);
        tool.register();

        assertNotNull(callback.get());
        assertEquals("python_analysis", callback.get().getToolDefinition().name());

        String code = realPython ? "import sys\nprint(sys.stdin.read())" : "cat";
        String input = new ObjectMapper().writeValueAsString(Map.of(
                "action", "execute_python",
                "code", code,
                "input", "legacy-tool-input",
                "requirement", "legacy-package==0.0.0"));
        String response = callback.get().call(input);

        assertTrue(response.contains("Python 分析结果"), response);
        assertTrue(response.contains("legacy-tool-input"), response);
    }
}
