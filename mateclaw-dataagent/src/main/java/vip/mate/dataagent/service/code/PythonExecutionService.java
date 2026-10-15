package vip.mate.dataagent.service.code;

import java.util.Map;

public interface PythonExecutionService {
    Map<String,Object> submit(Map<String,Object> request);
    Map<String,Object> getStatus(String taskId);
    Map<String,Object> cancel(String taskId);
}
