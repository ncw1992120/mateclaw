package vip.mate.dataagent.service.code;

import org.springframework.stereotype.Service;
import vip.mate.dataagent.dataset.DatasetColumn;
import vip.mate.dataagent.dataset.DatasetInputDescriptor;
import vip.mate.dataagent.dataset.ObjectRef;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 任务期不可变的脚本输入别名注册表 + prepared input 存储（内存态，重启即失效）。 */
@Service
public class ScriptTaskInputRegistry {
    private final Map<String, Map<String, DatasetInputDescriptor>> entries = new ConcurrentHashMap<>();
    private final Map<String, Map<String, PreparedInput>> preparedInputs = new ConcurrentHashMap<>();

    /** 已物化的 prepared input：完整行集或 ObjectRef 引用 + schema + rowCount + 过期时间。 */
    public record PreparedInput(String inputName, List<DatasetColumn> schema,
                                List<Map<String, Object>> inlineRows, ObjectRef objectRef,
                                long rowCount, long expiresAt) {
        public PreparedInput {
            schema = schema == null ? List.of() : List.copyOf(schema);
            inlineRows = inlineRows == null ? List.of() : inlineRows.stream().map(Map::copyOf).toList();
            if (inlineRows.isEmpty() && objectRef == null && rowCount == 0) {
                // 空输入是合法 prepared input（源端无匹配行）
            }
            if (inlineRows.size() > 0 && objectRef != null) {
                throw new IllegalArgumentException("inline rows and objectRef are mutually exclusive");
            }
        }

        public boolean expired() {
            return expiresAt > 0 && System.currentTimeMillis() > expiresAt;
        }
    }

    public void register(String taskId, Map<String, DatasetInputDescriptor> inputs) {
        if (taskId == null || taskId.isBlank() || inputs == null || inputs.isEmpty()) throw new IllegalArgumentException("task inputs are required");
        if (inputs.keySet().stream().anyMatch(n -> n == null || !n.matches("[A-Za-z][A-Za-z0-9_]{0,63}"))) throw new IllegalArgumentException("invalid input alias");
        if (entries.putIfAbsent(taskId, Map.copyOf(inputs)) != null) throw new IllegalStateException("task inputs already registered");
    }

    /** 注册 prepared input：仅允许任务注册后按已声明别名登记一次。 */
    public void registerPreparedInput(String taskId, PreparedInput input) {
        if (taskId == null || taskId.isBlank() || input == null || input.inputName() == null || input.inputName().isBlank()) {
            throw new IllegalArgumentException("prepared input is required");
        }
        if (!input.inputName().matches("[A-Za-z][A-Za-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("invalid input alias");
        }
        require(taskId, input.inputName()); // 别名必须是已声明输入
        preparedInputs.computeIfAbsent(taskId, key -> new ConcurrentHashMap<>())
                .put(input.inputName(), input);
    }

    public DatasetInputDescriptor require(String taskId, String inputName) {
        var inputs = entries.get(taskId); if (inputs == null || !inputs.containsKey(inputName)) throw new IllegalArgumentException("input alias is not declared"); return inputs.get(inputName);
    }

    /** 读取 prepared input：不存在或已过期分别给出可解释错误（不区分持久化存储）。 */
    public PreparedInput requirePreparedInput(String taskId, String inputName) {
        require(taskId, inputName);
        var inputs = preparedInputs.get(taskId);
        PreparedInput input = inputs == null ? null : inputs.get(inputName);
        if (input == null) {
            throw new IllegalStateException("prepared input is not ready: " + inputName);
        }
        if (input.expired()) {
            throw new PreparedInputExpiredException("prepared input expired: " + inputName);
        }
        return input;
    }

    public void remove(String taskId) {
        entries.remove(taskId);
        preparedInputs.remove(taskId);
    }

    /** prepared input 过期：稳定业务语义 PREPARED_INPUT_EXPIRED。 */
    public static class PreparedInputExpiredException extends IllegalStateException {
        public PreparedInputExpiredException(String message) { super(message); }
    }
}
