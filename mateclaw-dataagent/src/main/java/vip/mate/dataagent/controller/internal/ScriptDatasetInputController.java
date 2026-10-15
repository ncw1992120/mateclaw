package vip.mate.dataagent.controller.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vip.mate.common.result.R;
import vip.mate.dataagent.dataset.DatasetColumn;
import vip.mate.dataagent.dataset.ObjectRef;
import vip.mate.dataagent.objectref.DatasetBatchCodec;
import vip.mate.dataagent.objectref.ObjectRefService;
import vip.mate.dataagent.service.code.ScriptDatasetReadTokenService;
import vip.mate.dataagent.service.code.ScriptTaskInputRegistry;

import jakarta.servlet.http.HttpServletRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runner prepared input 读取接口：请求体只允许 {@code inputName}（+ 大结果的批次游标），
 * 服务端按 task/workspace/inputName 查 registry，不接受 datasetId 或任意查询条件。
 */
@RestController
@RequestMapping("/internal/v1/script-tasks")
@RequiredArgsConstructor
public class ScriptDatasetInputController {

    private static final int MAX_BATCH_ROWS = 10_000;

    private final ScriptTaskInputRegistry registry;
    private final ScriptDatasetReadTokenService tokens;
    private final ObjectRefService objectRefs;

    @PostMapping("/{taskId}/datasets/input")
    public R<Map<String, Object>> input(@PathVariable String taskId,
                                        @RequestHeader("Authorization") String authorization,
                                        HttpServletRequest request,
                                        @RequestBody InputBody body) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Bearer token required");
        }
        var claims = tokens.verify(authorization.substring(7));
        if (!taskId.equals(claims.taskId())) {
            throw new IllegalArgumentException("token task mismatch");
        }
        if (body == null || body.inputName() == null || body.inputName().isBlank()) {
            throw new IllegalArgumentException("inputName is required");
        }
        // 只允许 { inputName }（大结果续读允许 cursor/batchSize）；不接受 datasetId / columns / filters / parameters
        if (body.datasetId() != null || body.columns() != null || body.filters() != null || body.parameters() != null) {
            throw new IllegalArgumentException("prepared input request only accepts inputName");
        }
        ScriptTaskInputRegistry.PreparedInput input;
        try {
            input = registry.requirePreparedInput(taskId, body.inputName());
        } catch (ScriptTaskInputRegistry.PreparedInputExpiredException e) {
            throw new IllegalStateException("PREPARED_INPUT_EXPIRED: " + e.getMessage(), e);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inputName", input.inputName());
        payload.put("schema", schemaPayload(input.schema()));
        payload.put("rowCount", input.rowCount());
        if (input.objectRef() == null) {
            // 内联完整输入（可能是合法空结果）
            payload.put("rows", input.inlineRows());
            payload.put("objectRef", null);
            return R.ok(payload);
        }
        // 大结果：首批返回元数据，rows=null；带 cursor 续读批次
        if (body.cursor() == null) {
            payload.put("rows", null);
            payload.put("objectRef", input.objectRef());
            payload.put("batchSize", MAX_BATCH_ROWS);
            return R.ok(payload);
        }
        int batchSize = body.batchSize() == null || body.batchSize() <= 0 || body.batchSize() > MAX_BATCH_ROWS
                ? MAX_BATCH_ROWS : body.batchSize();
        var context = new vip.mate.dataagent.dataset.DatasetAccessContext(
                claims.workspaceId(), 0L, taskId, Set.of());
        List<Map<String, Object>> batchRows = DatasetBatchCodec.readRows(
                context, input.objectRef(), objectRefs, body.cursor(), batchSize);
        int nextCursor = body.cursor() + batchRows.size();
        payload.put("rows", batchRows);
        payload.put("cursor", nextCursor);
        payload.put("last", batchRows.isEmpty() || nextCursor >= input.rowCount());
        return R.ok(payload);
    }

    private List<Map<String, Object>> schemaPayload(List<DatasetColumn> schema) {
        return schema.stream().map(column -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", column.name());
            entry.put("dataType", column.dataType());
            entry.put("nullable", column.nullable());
            entry.put("semanticRole", column.semanticRole());
            return entry;
        }).toList();
    }

    /** prepared input 请求体：只接受 inputName；cursor/batchSize 供大结果受控批次续读。 */
    public record InputBody(String inputName, String datasetId, List<String> columns,
                            List<Object> filters, Map<String, Object> parameters,
                            Integer cursor, Integer batchSize) {
    }
}
