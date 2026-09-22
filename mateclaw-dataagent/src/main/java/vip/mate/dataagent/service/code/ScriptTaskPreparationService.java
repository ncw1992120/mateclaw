package vip.mate.dataagent.service.code;

import org.springframework.stereotype.Service;
import vip.mate.dataagent.dataset.*;
import vip.mate.dataagent.dto.DatasetQueryPlanDTO;
import vip.mate.dataagent.objectref.DatasetBatchCodec;
import vip.mate.dataagent.objectref.ObjectRefService;
import vip.mate.dataagent.service.DatasetManageService;

import java.util.*;

/** 在提交 Runner 前固化输入别名、权限上下文和短期读取令牌；有查询计划时物化 prepared input。 */
@Service
public class ScriptTaskPreparationService {
    /** prepared input 内联行数上限；超过则转 ObjectRef，由 SDK 受控批次读取。 */
    static final int INLINE_MAX_ROWS = 10_000;
    private static final int PAGE_SIZE = 10_000;
    private static final int MAX_PAGES = 10;
    private static final long PREPARED_INPUT_TTL_MS = 900_000L;

    private final DatasetManageService datasets; private final ScriptTaskInputRegistry registry; private final ScriptDatasetReadTokenService tokens;
    private final List<DatasetSourceAdapter> adapters; private final ObjectRefService objectRefs;
    public ScriptTaskPreparationService(DatasetManageService datasets, ScriptTaskInputRegistry registry,
                                        ScriptDatasetReadTokenService tokens, List<DatasetSourceAdapter> adapters,
                                        ObjectRefService objectRefs) {
        this.datasets = datasets; this.registry = registry; this.tokens = tokens; this.adapters = adapters; this.objectRefs = objectRefs;
    }

    public PreparedTask prepare(String taskId, Long workspaceId, Long userId, Map<String,Long> inputDatasets, String script, Map<String,Object> parameters) {
        return prepare(taskId, workspaceId, userId, inputDatasets, script, parameters, Map.of());
    }

    /**
     * 带查询计划的准备：在授权后执行 Query Planner 产出的计划（Adapter 物化），
     * 把完整且按任务隔离的行集或 ObjectRef、schema、rowCount 与过期时间注册到 registry。
     * 内存 registry 不是持久化存储，重启/过期后读取将得到明确失败。
     */
    public PreparedTask prepare(String taskId, Long workspaceId, Long userId, Map<String,Long> inputDatasets,
                                String script, Map<String,Object> parameters, Map<String, DatasetQueryPlanDTO> plans) {
        if (inputDatasets==null||inputDatasets.isEmpty()||script==null||script.isBlank()) throw new IllegalArgumentException("task inputs and script are required");
        DatasetAccessContext context=new DatasetAccessContext(workspaceId,userId,taskId,new HashSet<>(inputDatasets.values()));
        Map<String,DatasetInputDescriptor> descriptors=new LinkedHashMap<>(); inputDatasets.forEach((alias,id)->descriptors.put(alias,datasets.getInputDescriptor(context,id,alias))); registry.register(taskId,descriptors);
        String token=tokens.issue(taskId,workspaceId,900);
        Map<String, ScriptTaskInputRegistry.PreparedInput> prepared = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, DatasetQueryPlanDTO> entry : plans.entrySet()) {
                String alias = entry.getKey();
                DatasetQueryPlanDTO plan = entry.getValue();
                DatasetInputDescriptor descriptor = descriptors.get(alias);
                if (descriptor == null) throw new IllegalArgumentException("plan references unknown input: " + alias);
                prepared.put(alias, materialize(context, alias, descriptor, plan));
            }
        } catch (RuntimeException e) {
            registry.remove(taskId); // 失败不留半注册状态
            throw e;
        }
        prepared.values().forEach(input -> registry.registerPreparedInput(taskId, input));
        return new PreparedTask(taskId,script,descriptors,parameters==null?Map.of():Map.copyOf(parameters),token,prepared);
    }
    public record PreparedTask(String taskId,String script,Map<String,DatasetInputDescriptor> inputCatalog,Map<String,Object> parameters,String readToken,
                               Map<String, ScriptTaskInputRegistry.PreparedInput> preparedInputs) {
        public PreparedTask(String taskId, String script, Map<String, DatasetInputDescriptor> inputCatalog,
                            Map<String, Object> parameters, String readToken) {
            this(taskId, script, inputCatalog, parameters, readToken, Map.of());
        }
    }

    /** 按计划分页读取源端并累积为有界完整输入；超过内联上限时转 ObjectRef。 */
    private ScriptTaskInputRegistry.PreparedInput materialize(DatasetAccessContext context, String alias,
                                                              DatasetInputDescriptor descriptor, DatasetQueryPlanDTO plan) {
        if (plan.shortCircuitEmpty()) {
            // 显式空集合：不访问数据源，也不需要 Adapter
            return new ScriptTaskInputRegistry.PreparedInput(alias, descriptor.schema(), List.of(), null, 0,
                    System.currentTimeMillis() + PREPARED_INPUT_TTL_MS);
        }
        DatasetSourceAdapter adapter = adapters.stream().filter(a -> a.supports(descriptor.sourceType())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no adapter for plan input: " + alias));
        int readLimit = plan.readLimit() != null ? Math.min(plan.readLimit(), 100_000) : PAGE_SIZE;
        int offset = plan.pushdown().pagination() && plan.pagination() != null ? plan.pagination().offset() : 0;
        int effectiveLimit = plan.pushdown().pagination() && plan.pagination() != null
                ? plan.pagination().pageSize() : readLimit;
        List<Map<String, Object>> rows = new ArrayList<>();
        int pageOffset = offset;
        int pages = 0;
        while (pages++ < MAX_PAGES && rows.size() < effectiveLimit) {
            int size = Math.min(PAGE_SIZE, effectiveLimit - rows.size());
            DatasetBatch batch = adapter.read(context, new DatasetReadRequest(datasetIdOf(plan, context, alias), alias,
                    plan.columns(), filtersOf(plan), plan.orders().stream()
                            .map(o -> new DatasetSort(o.field(), o.direction())).toList(),
                    size, pageOffset, Map.of(), false));
            List<Map<String, Object>> pageRows = batch.rows() == null ? List.of() : batch.rows();
            rows.addAll(pageRows);
            if (pageRows.size() < size || batch.last()) break;
            pageOffset += pageRows.size();
        }
        long expiresAt = System.currentTimeMillis() + PREPARED_INPUT_TTL_MS;
        if (rows.size() > INLINE_MAX_ROWS) {
            DatasetBatch spill = new DatasetBatch(rows, null, rows.size(), true, null);
            ObjectRef reference = DatasetBatchCodec.writeParquet(context, spill, objectRefs);
            return new ScriptTaskInputRegistry.PreparedInput(alias, descriptor.schema(), List.of(), reference, rows.size(), expiresAt);
        }
        return new ScriptTaskInputRegistry.PreparedInput(alias, descriptor.schema(), rows, null, rows.size(), expiresAt);
    }

    private long datasetIdOf(DatasetQueryPlanDTO plan, DatasetAccessContext context, String alias) {
        String id = plan.datasetId();
        if (id == null || id.isBlank()) throw new IllegalArgumentException("plan datasetId is required for input: " + alias);
        try {
            long parsed = Long.parseLong(id);
            if (!context.canRead(parsed)) throw new DatasetReadException(DatasetReadErrorCode.ACCESS_DENIED, "无权读取计划数据集");
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("plan datasetId is not numeric: " + id);
        }
    }

    private List<DatasetFilter> filtersOf(DatasetQueryPlanDTO plan) {
        return plan.filters().stream()
                .map(f -> new DatasetFilter(f.field(), "dimension", f.operator(), f.value()))
                .toList();
    }
}
