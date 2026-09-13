package vip.mate.dataagent.service.code;

import org.springframework.stereotype.Service;
import vip.mate.dataagent.dataset.DatasetInputDescriptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 任务期不可变的脚本输入别名注册表。 */
@Service
public class ScriptTaskInputRegistry {
    private final Map<String, Map<String, DatasetInputDescriptor>> entries = new ConcurrentHashMap<>();
    public void register(String taskId, Map<String, DatasetInputDescriptor> inputs) {
        if (taskId == null || taskId.isBlank() || inputs == null || inputs.isEmpty()) throw new IllegalArgumentException("task inputs are required");
        if (inputs.keySet().stream().anyMatch(n -> n == null || !n.matches("[A-Za-z][A-Za-z0-9_]{0,63}"))) throw new IllegalArgumentException("invalid input alias");
        if (entries.putIfAbsent(taskId, Map.copyOf(inputs)) != null) throw new IllegalStateException("task inputs already registered");
    }
    public DatasetInputDescriptor require(String taskId, String inputName) {
        var inputs = entries.get(taskId); if (inputs == null || !inputs.containsKey(inputName)) throw new IllegalArgumentException("input alias is not declared"); return inputs.get(inputName);
    }
    public void remove(String taskId) { entries.remove(taskId); }
}
