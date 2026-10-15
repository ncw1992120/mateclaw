package vip.mate.dataagent.service;

import vip.mate.dataagent.dto.DashboardExecutionRequest;

import java.util.Map;

/** 仪表盘 Python 执行编排；只从已保存 Schema 读取脚本和输入绑定。 */
public interface DashboardExecutionService {
    Map<String, Object> submit(long dashboardId, DashboardExecutionRequest request);
    Map<String, Object> status(String executionId);
    Map<String, Object> cancel(String executionId);
    Map<String, Object> logs(String executionId);
    Map<String, Object> result(String executionId);
}
