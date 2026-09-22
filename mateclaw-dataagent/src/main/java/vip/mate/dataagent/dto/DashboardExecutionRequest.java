package vip.mate.dataagent.dto;

import java.util.Map;

/** 仪表盘脚本执行的运行时参数；脚本和输入绑定来自已保存的 Dashboard Schema。 */
public record DashboardExecutionRequest(Map<String, Object> parameters, String componentId, String schemaJson) {
    public DashboardExecutionRequest(Map<String, Object> parameters) {
        this(parameters, null, null);
    }
    public DashboardExecutionRequest(Map<String, Object> parameters, String componentId) {
        this(parameters, componentId, null);
    }
    public DashboardExecutionRequest {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        componentId = componentId == null || componentId.isBlank() ? null : componentId;
        schemaJson = schemaJson == null || schemaJson.isBlank() ? null : schemaJson;
    }
}
