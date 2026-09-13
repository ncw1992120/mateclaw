package vip.mate.dataagent.service.code;

import org.junit.jupiter.api.Test;
import vip.mate.dataagent.service.code.impl.LocalCodeExecutorService;

import static org.junit.jupiter.api.Assertions.*;

/** 旧 Agent Python 路径保留 requirements/pip 兼容行为；新 Runner 的拒绝策略不应影响它。 */
class LocalCodeExecutorCompatibilityTest {
    @Test
    void legacyExecutorStillAcceptsRequirementWithoutChangingExecutionContract() {
        var properties = new CodeExecutorProperties();
        boolean realPython = "true".equalsIgnoreCase(System.getenv("MATECLAW_LEGACY_REAL_PYTHON"));
        // 默认 Maven JDK 镜像不预装 Python，使用 /bin/sh 验证旧执行器的
        // requirements 流程；带 Python 的验收容器可显式开启真实解释器模式。
        properties.setPythonCommand(realPython ? "python3" : "/bin/sh");
        properties.setPipCommand("/bin/false");
        properties.setPipTimeoutSeconds(1);
        properties.setCodeTimeoutSeconds(5);
        properties.setEnabled(true);

        String code = realPython ? "print('legacy-compatible')" : "printf legacy-compatible";
        var response = new LocalCodeExecutorService(properties).execute(
                new CodeExecutorService.TaskRequest(code, "", "legacy-package==0.0.0"));

        assertTrue(response.isSuccess(), () -> "legacy executor failed: " + response.exceptionMsg());
        assertTrue(response.stdOut().contains("legacy-compatible"));
    }
}
