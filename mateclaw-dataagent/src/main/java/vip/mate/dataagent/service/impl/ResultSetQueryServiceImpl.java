package vip.mate.dataagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.dataset.DatasetAccessContext;
import vip.mate.dataagent.dataset.DatasetSort;
import vip.mate.dataagent.dataset.ObjectRef;
import vip.mate.dataagent.dataset.ResidualRowOperations;
import vip.mate.dataagent.dto.QueryContextDTO;
import vip.mate.dataagent.model.DashboardExecutionEntity;
import vip.mate.dataagent.objectref.DatasetBatchCodec;
import vip.mate.dataagent.objectref.ObjectRefService;
import vip.mate.dataagent.repository.DashboardExecutionMapper;
import vip.mate.dataagent.service.QueryPlanErrorCodes;
import vip.mate.dataagent.service.QueryPlanException;
import vip.mate.dataagent.service.ResultSetQueryService;
import vip.mate.dataagent.service.code.ScriptResultContractService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 结果集分页/排序查询实现：
 * - 内联结果：完整行集内存排序 + 切片，totalCount 精确；
 * - ObjectRef 结果：受控分批读全量（有界）后同样处理，ObjectRef 过期/缺失时显式失败；
 * - 未就绪/失败状态映射稳定错误码，不把堆栈或令牌返回浏览器。
 */
@Service
public class ResultSetQueryServiceImpl implements ResultSetQueryService {

    /** 结果集阶段允许的最大排序/总数计算行数（有界残余处理上限）。 */
    static final int MAX_RESULT_ROWS = 100_000;
    private static final int PAGE_SIZE = 10_000;

    private final DashboardExecutionMapper executionMapper;
    private final WorkspaceGuard workspaceGuard;
    private final ObjectMapper mapper;
    private final ScriptResultContractService resultContract;
    private final ObjectRefService objectRefs;
    private final String resultTtlMillis;

    public ResultSetQueryServiceImpl(DashboardExecutionMapper executionMapper, WorkspaceGuard workspaceGuard,
                                     ObjectMapper mapper, ScriptResultContractService resultContract,
                                     ObjectRefService objectRefs,
                                     @Value("${mateclaw.runner.result-ttl-millis:0}") String resultTtlMillis) {
        this.executionMapper = executionMapper;
        this.workspaceGuard = workspaceGuard;
        this.mapper = mapper;
        this.resultContract = resultContract;
        this.objectRefs = objectRefs;
        this.resultTtlMillis = resultTtlMillis;
    }

    @Override
    public Map<String, Object> preview(String executionId, QueryContextDTO context) {
        if (executionId == null || executionId.isBlank()) {
            throw QueryPlanException.of(QueryPlanErrorCodes.QUERY_CONTEXT_INVALID, "unknown dashboard execution");
        }
        DashboardExecutionEntity execution = executionMapper.selectOne(new LambdaQueryWrapper<DashboardExecutionEntity>()
                .eq(DashboardExecutionEntity::getExecutionId, executionId)
                .eq(DashboardExecutionEntity::getWorkspaceId, workspaceGuard.currentWorkspaceId()));
        if (execution == null) {
            throw QueryPlanException.of(QueryPlanErrorCodes.QUERY_CONTEXT_INVALID, "unknown dashboard execution");
        }
        requireTerminal(execution);

        List<Map<String, Object>> rows = loadTableRows(execution);
        int totalCount = rows.size();
        if (context != null && context.sort() != null) {
            ResidualRowOperations.sort(rows, List.of(new DatasetSort(context.sort().field(), context.sort().direction())));
        }
        int page = context != null && context.pagination() != null ? context.pagination().page() : 1;
        int pageSize = context != null && context.pagination() != null ? context.pagination().pageSize() : Math.min(totalCount, 100);
        List<Map<String, Object>> pageRows = new ArrayList<>(ResidualRowOperations.paginate(rows, pageSize, (page - 1) * pageSize));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("executionId", execution.getExecutionId());
        response.put("status", execution.getStatus());
        response.put("columns", columnNames(execution, rows));
        response.put("rows", pageRows);
        response.put("page", page);
        response.put("pageSize", pageSize);
        response.put("totalCount", totalCount);
        if (context != null && context.requestId() != null) response.put("requestId", context.requestId());
        return response;
    }

    /** 未就绪/失败/契约错误分别映射稳定错误码。 */
    private void requireTerminal(DashboardExecutionEntity execution) {
        String status = execution.getStatus() == null ? "" : execution.getStatus();
        switch (status) {
            case "SUCCEEDED", "RESULT_REF" -> { }
            case "SUBMITTING", "RUNNING" -> throw QueryPlanException.of(QueryPlanErrorCodes.RESULT_NOT_READY,
                    "execution is still running: " + status);
            case "TIMEOUT" -> throw QueryPlanException.of(QueryPlanErrorCodes.RESULT_NOT_READY, "execution timed out");
            case "OUTPUT_CONTRACT_ERROR" -> throw QueryPlanException.of(QueryPlanErrorCodes.QUERY_CONTEXT_INVALID,
                    "execution output failed the result contract");
            default -> throw QueryPlanException.of(QueryPlanErrorCodes.QUERY_CONTEXT_INVALID,
                    "execution did not produce a usable result: " + status);
        }
    }

    private List<Map<String, Object>> loadTableRows(DashboardExecutionEntity execution) {
        try {
            if (execution.getOutputJson() != null && !execution.getOutputJson().isBlank()) {
                Object envelope = mapper.readValue(execution.getOutputJson(), Object.class);
                ScriptResultContractService.ValidatedEnvelope validated = resultContract.validate(envelope);
                if (!"table".equals(validated.kind())) {
                    throw QueryPlanException.of(QueryPlanErrorCodes.QUERY_CONTEXT_INVALID,
                            "execution result is not a table: " + validated.kind());
                }
                return new ArrayList<>(validated.data().rows());
            }
            if (execution.getOutputRefJson() != null && !"null".equals(execution.getOutputRefJson())) {
                ObjectRef reference = mapper.readValue(execution.getOutputRefJson(), ObjectRef.class);
                DatasetAccessContext accessContext = new DatasetAccessContext(
                        execution.getWorkspaceId(), 0L, execution.getExecutionId(), java.util.Set.of());
                List<Map<String, Object>> rows = new ArrayList<>();
                int offset = 0;
                while (rows.size() < MAX_RESULT_ROWS) {
                    List<Map<String, Object>> batch = DatasetBatchCodec.readRows(accessContext, reference, objectRefs, offset, PAGE_SIZE);
                    rows.addAll(batch);
                    if (batch.size() < PAGE_SIZE) break;
                    offset += batch.size();
                }
                if (rows.size() > MAX_RESULT_ROWS) {
                    throw QueryPlanException.of(QueryPlanErrorCodes.QUERY_CONTEXT_INVALID,
                            "result exceeds the bounded result-stage limit " + MAX_RESULT_ROWS);
                }
                ScriptResultContractService.ValidatedEnvelope rebuilt = resultContract.rebuildTable(
                        mapper.readValue(execution.getOutputRefJson(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}),
                        rows);
                return new ArrayList<>(rebuilt.data().rows());
            }
            throw QueryPlanException.of(QueryPlanErrorCodes.QUERY_CONTEXT_INVALID, "execution has no result");
        } catch (QueryPlanException e) {
            throw e;
        } catch (Exception e) {
            // ObjectRef 过期/缺失/损坏：统一可解释失败，不透出内部细节
            throw QueryPlanException.of(QueryPlanErrorCodes.QUERY_CONTEXT_INVALID,
                    "dashboard execution result unavailable");
        }
    }

    private List<String> columnNames(DashboardExecutionEntity execution, List<Map<String, Object>> rows) {
        if (!rows.isEmpty()) return new ArrayList<>(rows.getFirst().keySet());
        try {
            if (execution.getOutputRefJson() != null && !"null".equals(execution.getOutputRefJson())) {
                Map<String, Object> meta = mapper.readValue(execution.getOutputRefJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                Object columns = meta.get("columns");
                if (columns instanceof List<?> list) {
                    return list.stream().map(item -> String.valueOf(((Map<?, ?>) item).get("name"))).toList();
                }
            }
        } catch (Exception ignored) {
            // 列名推断失败时返回空列表，不阻断分页响应
        }
        return List.of();
    }
}
