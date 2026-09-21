package vip.mate.dataagent.service.code;

import java.util.Map;

/** Runner 输出契约校验失败：携带精确字段路径、期望/实际类型与修复建议。 */
public class ScriptResultContractException extends RuntimeException {
    private final String path;
    private final String expected;
    private final String actual;
    private final String suggestion;

    public ScriptResultContractException(String path, String expected, String actual, String suggestion) {
        super("output contract violation at " + path + ": expected " + expected + ", got " + actual + "; " + suggestion);
        this.path = path;
        this.expected = expected;
        this.actual = actual;
        this.suggestion = suggestion;
    }

    public String getPath() { return path; }
    public String getExpected() { return expected; }
    public String getActual() { return actual; }
    public String getSuggestion() { return suggestion; }

    /** 结构化错误对象（写入执行错误与 API 响应）。 */
    public Map<String, String> asMap() {
        return Map.of("stage", "output-validation", "path", path, "expected", expected,
                "actual", actual, "suggestion", suggestion);
    }
}
