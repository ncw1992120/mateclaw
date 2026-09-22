package vip.mate.dataagent.controller.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import vip.mate.dataagent.dataset.DatasetColumn;
import vip.mate.dataagent.dataset.DatasetInputDescriptor;
import vip.mate.dataagent.dataset.DatasetSourceType;
import vip.mate.dataagent.objectref.ObjectRefService;
import vip.mate.dataagent.service.code.ScriptDatasetReadTokenService;
import vip.mate.dataagent.service.code.ScriptTaskInputRegistry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** prepared input 读取契约（实施计划任务 4）：只接受 inputName，令牌绑定 task，过期/未准备可解释。 */
class ScriptDatasetInputControllerTest {

    private ScriptDatasetInputController controller;
    private ScriptTaskInputRegistry registry;
    private ScriptDatasetReadTokenService tokens;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        registry = new ScriptTaskInputRegistry();
        tokens = new ScriptDatasetReadTokenService("unit-secret");
        controller = new ScriptDatasetInputController(registry, tokens, mock(ObjectRefService.class));
        registry.register("task-1", Map.of("strategy_data", new DatasetInputDescriptor(42L, "strategy_data",
                DatasetSourceType.JDBC_TABLE, List.of(), null, Map.of(), null)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String taskId, String token, Map<String, Object> body) throws Exception {
        var parsed = mapper.readValue(mapper.writeValueAsString(body), ScriptDatasetInputController.InputBody.class);
        var response = controller.input(taskId, "Bearer " + token,
                new MockHttpServletRequest(), parsed);
        return mapper.convertValue(response.getData(), Map.class);
    }

    @Test
    @DisplayName("内联 prepared input：响应含 schema/rowCount/rows，objectRef 为空")
    void inlineInput() throws Exception {
        registry.registerPreparedInput("task-1", new ScriptTaskInputRegistry.PreparedInput("strategy_data",
                List.of(new DatasetColumn("strategy_id", "策略", "STRING", true, "dimension"),
                        new DatasetColumn("in_account", "入金", "DECIMAL", true, "measure")),
                List.of(Map.of("strategy_id", "A", "in_account", 120.5),
                        Map.of("strategy_id", "B", "in_account", 80.0)),
                null, 2, System.currentTimeMillis() + 60_000));
        Map<String, Object> data = call("task-1", tokens.issue("task-1", 7L, 900),
                Map.of("inputName", "strategy_data"));
        assertEquals("strategy_data", data.get("inputName"));
        assertEquals(2, ((Number) data.get("rowCount")).intValue());
        assertEquals(2, ((List<?>) data.get("rows")).size());
        assertTrue(data.get("objectRef") == null || !data.containsKey("objectRef"));
        assertEquals("strategy_id", ((List<Map<String, Object>>) data.get("schema")).getFirst().get("name"));
    }

    @Test
    @DisplayName("请求携带 datasetId/filters 等页面参数被拒绝；伪造令牌任务被拒绝")
    void rejectsExtraFieldsAndTokenMismatch() {
        var bad = assertThrows(IllegalArgumentException.class, () -> call("task-1",
                tokens.issue("task-1", 7L, 900),
                Map.of("inputName", "strategy_data",
                        "filters", List.of(Map.of("field", "strategy_id", "operator", "eq", "value", "A")))));
        assertTrue(bad.getMessage().contains("only accepts inputName"));
        String otherTask = tokens.issue("task-9", 7L, 900);
        assertThrows(IllegalArgumentException.class, () -> call("task-1", otherTask,
                Map.of("inputName", "strategy_data")));
    }

    @Test
    @DisplayName("别名不存在与输入未准备返回可解释错误")
    void unknownAliasAndNotReady() {
        assertThrows(IllegalArgumentException.class, () -> call("task-1",
                tokens.issue("task-1", 7L, 900), Map.of("inputName", "ghost")));
        var notReady = assertThrows(IllegalStateException.class, () -> call("task-1",
                tokens.issue("task-1", 7L, 900), Map.of("inputName", "strategy_data")));
        assertTrue(notReady.getMessage().contains("not ready"));
    }

    @Test
    @DisplayName("过期 prepared input 返回 PREPARED_INPUT_EXPIRED")
    void expiredInput() {
        registry.registerPreparedInput("task-1", new ScriptTaskInputRegistry.PreparedInput("strategy_data",
                List.of(), List.of(), null, 0, System.currentTimeMillis() - 1));
        var expired = assertThrows(IllegalStateException.class, () -> call("task-1",
                tokens.issue("task-1", 7L, 900), Map.of("inputName", "strategy_data")));
        assertTrue(expired.getMessage().contains("PREPARED_INPUT_EXPIRED"));
    }

    @Test
    @DisplayName("空结果是合法 prepared input：rowCount=0 rows=[]")
    void emptyInputIsValid() throws Exception {
        registry.registerPreparedInput("task-1", new ScriptTaskInputRegistry.PreparedInput("strategy_data",
                List.of(new DatasetColumn("strategy_id", "策略", "STRING", true, "dimension")),
                List.of(), null, 0, System.currentTimeMillis() + 60_000));
        Map<String, Object> data = call("task-1", tokens.issue("task-1", 7L, 900),
                Map.of("inputName", "strategy_data"));
        assertEquals(0, ((Number) data.get("rowCount")).intValue());
        assertTrue(((List<?>) data.get("rows")).isEmpty());
    }
}
