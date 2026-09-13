package vip.mate.dataagent.dto;

import java.util.Map;

/** 仪表盘脚本执行的运行时参数；脚本和输入绑定来自已保存的 Dashboard Schema。 */
public record DashboardExecutionRequest(Map<String, Object> parameters) {
    public DashboardExecutionRequest {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
