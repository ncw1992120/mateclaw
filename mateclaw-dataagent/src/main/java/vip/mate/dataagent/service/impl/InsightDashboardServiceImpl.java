package vip.mate.dataagent.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import vip.mate.dataagent.agentscope.dto.AgentCallRequest;
import vip.mate.dataagent.agentscope.service.AgentScopeService;
import vip.mate.dataagent.auth.context.UserContext;
import vip.mate.dataagent.auth.context.UserContextHolder;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.*;
import vip.mate.dataagent.exception.BusinessException;
import vip.mate.dataagent.model.InsightDashboardEntity;
import vip.mate.dataagent.repository.InsightDashboardMapper;
import vip.mate.dataagent.service.AloudataSemanticEsService;
import vip.mate.dataagent.service.SchemaEmbeddingService;
import vip.mate.dataagent.service.DatasourceManageService;
import vip.mate.dataagent.service.InsightDashboardService;
import vip.mate.dataagent.support.Utf8SseEmitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 洞察仪表盘服务实现
 * <p>
 * 按工作区隔离仪表盘资源，CRUD 操作均校验归属权限。
 */
@Slf4j
@Service
public class InsightDashboardServiceImpl implements InsightDashboardService {

    private final InsightDashboardMapper insightDashboardMapper;
    private final WorkspaceGuard workspaceGuard;
    private final DatasourceManageService datasourceManageService;
    private final AloudataSemanticEsService aloudataSemanticEsService;
    private final SchemaEmbeddingService schemaEmbeddingService;
    private final ObjectMapper objectMapper;
    private final AgentScopeService agentScopeService;
    private final ExecutorService aiChatExecutor;
    /** SSE心跳调度器，在LLM空闲阶段周期性发送ping事件保活 */
    private final ScheduledExecutorService heartbeatScheduler;

    /** AI助手生成仪表盘的系统提示词 */
    private static final String GENERATE_SYSTEM_PROMPT = """
            你是一个数据可视化专家。你的任务是根据用户提供的数据源信息和需求描述，生成仪表盘的Schema JSON。

            ## 数据源信息
            %s

            ## 用户需求
            %s

            ## 要求
            1. 严格返回JSON格式，不要包含任何解释性文字
            2. JSON结构如下：
            {
              "version": "1.0",
              "pages": [
                {
                  "id": "page_0",
                  "name": "页面名称",
                  "order": 0,
                  "components": [
                    {
                      "id": "comp_0",
                      "type": "组件类型",
                      "title": "组件标题",
                      "position": {"x": 0, "y": 0, "w": 12, "h": 4},
                      "chartType": "图表子类型(仅chart类型需要)",
                      "renderType": "渲染类型",
                      "dataSource": {
                        "datasourceId": "%s",
                        "metrics": ["指标名1", "指标名2"],
                        "dimensions": ["维度名1"],
                        "filters": [],
                        "limit": 100
                      }
                    }
                  ]
                }
              ]
            }

            3. 组件类型(type)说明：
               - "chart": 图表组件，必须指定chartType和renderType
               - "kpi": KPI指标卡片，renderType为"kpi"
               - "table": 表格组件，renderType为"table"
               - "filter": 筛选器组件，不需要dataSource
               - "timeFilter": 时间筛选器组件，不需要dataSource，需要config: {"field": "metric_time", "availablePresets": ["today","7d","30d","90d","custom"]}

            4. 图表子类型(chartType)可选值：line(折线图)、bar(柱状图)、pie(饼图)、area(面积图)、scatter(散点图)、radar(雷达图)
            5. renderType对应关系：chart类型 -> "echarts"，kpi类型 -> "kpi"，table类型 -> "table"
            6. position说明：栅格布局，总宽度24列。w=12表示半宽，w=24表示全宽。h=4为默认高度。x、y、w、h 均为整数，w 范围 1-24，h 范围 1-30。
            7. 组件布局要求：每行放2个组件（w=12），从上到下依次排列，y值递增
            8. metrics 必须使用数据源信息中列出的指标英文名(metricName)，dimensions 必须使用维度英文名(dimName)。不要使用物理字段名或自造名称。
            9. 根据用户需求合理选择图表类型：
               - 趋势分析 -> line/area
               - 对比分析 -> bar
               - 占比分布 -> pie
               - 关键指标 -> kpi
               - 明细数据 -> table
            10. 每个仪表盘建议包含3-8个组件，覆盖用户需求的不同方面
            11. 如果用户需求涉及筛选，添加filter或timeFilter组件
            """;

    /** AI助手修改仪表盘的系统提示词（增量操作指令模式） */
    private static final String MODIFY_SYSTEM_PROMPT = """
            你是一个数据可视化专家。你的任务是根据用户的修改指令，对已有仪表盘输出【操作指令列表】，由系统逐条应用。
            不要返回完整 Schema，只返回需要变更的操作。

            ## 当前仪表盘组件清单（id | type | title | position）
            %s

            ## 数据源可用字段（用于 add/update 时填写 dataSource）
            %s

            ## 用户修改指令
            %s

            ## 输出要求
            1. 严格返回 JSON 数组，不要包含任何解释性文字、不要 markdown 代码块
            2. 数组每个元素是一个操作指令，结构：{"op": "...", "id": "...", ...}
            3. 只输出用户指令涉及的操作，未提及的组件不要任何指令
            4. op 可选值与字段说明：

               - resize：调整组件尺寸。提供 id、w、h。
                 w 范围 1-24（24列栅格，12=半宽，24=全宽）；h 范围 1-30。
                 注意「调大/调小」要给出明显变化的数值，例如 w:12→w:20 才算明显调大，不要只 +1。
               - move：调整组件位置。提供 id、x、y。x 范围 0-23，y 范围 0-100。
               - add：新增组件。提供 type、title、chartType(type=chart时必填)、dataSource(非筛选器类必填)。
                 id 可留空（系统自动生成）；position 可留空（系统自动布局到末尾）。
               - delete：删除组件。提供 id。
               - update：修改组件属性（标题/图表类型/数据源/配置等，不含位置）。提供 id 及要修改的字段。
               - add-page：新增页面。提供 pageName（必填，新页面名称）；可选 pageIcon、pageParentId（设为已有页面ID则作为子页面）、pageOrder。
                 页面 id 由系统自动生成；新页面初始为空，可后续用 add 指令往 pageId（新页面ID）里加组件。
                 注意：add-page 返回后会得到新页面 id，若同一条消息里还要往新页面加组件，请用占位 id（如 "new_page_1"）并在 add 指令的 pageId 中引用同一占位 id，系统会自动关联。
               - delete-page：删除页面及其所有组件。提供 id（要删除的页面ID）。
               - rename-page：重命名页面（或修改图标/层级）。提供 id（页面ID）和 pageName；可选 pageIcon、pageParentId、pageOrder。

            5. 组件类型(type)：kpi、chart、table、filter、timeFilter、aiAnalysis
            6. 图表子类型(chartType)：line、bar、pie、area、scatter、radar
            7. dataSource 结构：{"datasourceId":"数据源ID","metrics":["指标名"],"dimensions":["维度名"],"filters":[],"limit":100}
            8. filter 组件不需要 dataSource；timeFilter 组件不需要 dataSource，config 为 {"field":"metric_time","availablePresets":["today","7d","30d","90d","custom"]}

            ## 示例
            用户「把销售额卡片调大一点」→ [{"op":"resize","id":"comp_0","w":20,"h":8}]
            用户「删除第三个图表」→ [{"op":"delete","id":"comp_2"}]
            用户「加一个按地区分布的饼图」→ [{"op":"add","type":"chart","title":"地区分布","chartType":"pie","dataSource":{"datasourceId":"1","metrics":["销售额"],"dimensions":["地区"],"filters":[],"limit":100}}]
            用户「把柱状图改成折线图」→ [{"op":"update","id":"comp_1","chartType":"line"}]
            用户「新增一个页面叫趋势分析」→ [{"op":"add-page","pageName":"趋势分析"}]
            用户「新增一个页面并加个折线图」→ [{"op":"add-page","pageName":"趋势分析"},{"op":"add","pageId":"new_page_1","type":"chart","title":"销售趋势","chartType":"line","dataSource":{"datasourceId":"1","metrics":["销售额"],"dimensions":["日期"],"filters":[],"limit":100}}]
            用户「删除第二个页面」→ [{"op":"delete-page","id":"page_1"}]
            用户「把首页改名叫总览」→ [{"op":"rename-page","id":"page_0","pageName":"总览"}]
            用户「把第二个页面移到第一位」→ [{"op":"move-page","id":"page_1","pageOrder":0}]
            """;

    /** SSE超时时间（10分钟） */
    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L;

    /** SSE心跳间隔（15秒），用于在LLM思考等空闲阶段保活，避免被中间代理的空闲超时（通常60s）掐断 */
    private static final long SSE_HEARTBEAT_INTERVAL_MS = 15_000L;

    public InsightDashboardServiceImpl(InsightDashboardMapper insightDashboardMapper,
                                       WorkspaceGuard workspaceGuard,
                                       DatasourceManageService datasourceManageService,
                                       AloudataSemanticEsService aloudataSemanticEsService,
                                       SchemaEmbeddingService schemaEmbeddingService,
                                       ObjectMapper objectMapper,
                                       AgentScopeService agentScopeService) {
        this.insightDashboardMapper = insightDashboardMapper;
        this.workspaceGuard = workspaceGuard;
        this.datasourceManageService = datasourceManageService;
        this.aloudataSemanticEsService = aloudataSemanticEsService;
        this.schemaEmbeddingService = schemaEmbeddingService;
        this.objectMapper = objectMapper;
        this.agentScopeService = agentScopeService;
        int maxThreads = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
        this.aiChatExecutor = new ThreadPoolExecutor(
                2, maxThreads,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(256),
                r -> {
                    Thread t = new Thread(r, "insight-ai-chat");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        this.heartbeatScheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "insight-sse-heartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public List<InsightDashboardVO> listDashboards() {
        LambdaQueryWrapper<InsightDashboardEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InsightDashboardEntity::getWorkspaceId, workspaceGuard.currentWorkspaceId());
        wrapper.orderByDesc(InsightDashboardEntity::getUpdateTime);
        List<InsightDashboardEntity> entities = insightDashboardMapper.selectList(wrapper);
        return entities.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public InsightDashboardVO getDashboard(Long id) {
        requireOwnership(id);
        InsightDashboardEntity entity = insightDashboardMapper.selectById(id);
        return toVO(entity);
    }

    @Override
    public InsightDashboardVO createDashboard(InsightDashboardCreateRequest request) {
        InsightDashboardEntity entity = new InsightDashboardEntity();
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setSchemaJson(request.getSchemaJson() != null ? request.getSchemaJson() : "{\"version\":\"1.0\",\"components\":[]}");
        entity.setStatus(DataAgentConstants.INSIGHT_DASHBOARD_STATUS_DRAFT);
        entity.setAgentId(request.getAgentId());
        entity.setWorkspaceId(workspaceGuard.currentWorkspaceId());
        entity.setOwnerId(workspaceGuard.currentUserId());
        entity.setOwnerName(request.getOwnerName() != null ? request.getOwnerName() : workspaceGuard.currentUserNickname());
        entity.setDeleted(0);
        insightDashboardMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    public InsightDashboardVO updateDashboard(Long id, InsightDashboardUpdateRequest request) {
        requireOwnership(id);
        InsightDashboardEntity entity = insightDashboardMapper.selectById(id);
        if (request.getName() != null) {
            entity.setName(request.getName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getSchemaJson() != null) {
            entity.setSchemaJson(request.getSchemaJson());
        }
        if (request.getReportContent() != null) {
            entity.setReportContent(request.getReportContent());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        if (request.getAgentId() != null) {
            entity.setAgentId(request.getAgentId());
        }
        if (request.getOwnerName() != null) {
            entity.setOwnerName(request.getOwnerName());
        }
        entity.setModifier(workspaceGuard.currentUserNickname());
        insightDashboardMapper.updateById(entity);
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDashboard(Long id) {
        requireOwnership(id);
        int rows = insightDashboardMapper.deleteById(id);
        log.info("删除仪表盘: id={}, 影响行数={}", id, rows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InsightDashboardVO copyDashboard(Long id) {
        requireOwnership(id);
        InsightDashboardEntity source = insightDashboardMapper.selectById(id);
        if (source == null || source.getDeleted() == 1) {
            throw new BusinessException(404, "仪表盘不存在: " + id);
        }

        InsightDashboardEntity copy = new InsightDashboardEntity();
        BeanUtils.copyProperties(source, copy);
        copy.setId(null);
        copy.setName(generateCopyName(source.getName()));
        copy.setStatus(DataAgentConstants.INSIGHT_DASHBOARD_STATUS_DRAFT);
        copy.setOwnerId(workspaceGuard.currentUserId());
        copy.setOwnerName(workspaceGuard.currentUserNickname());
        copy.setWorkspaceId(workspaceGuard.currentWorkspaceId());
        copy.setSchemaJson(remapSchemaIds(source.getSchemaJson()));
        copy.setModifier(null);
        copy.setCreateTime(null);
        copy.setUpdateTime(null);
        copy.setDeleted(0);

        insightDashboardMapper.insert(copy);
        log.info("复制仪表盘: sourceId={}, newId={}, name={}", id, copy.getId(), copy.getName());
        return toVO(copy);
    }

    /**
     * 生成复制后的仪表盘名称
     */
    private String generateCopyName(String originalName) {
        String suffix = DataAgentConstants.INSIGHT_DASHBOARD_COPY_SUFFIX;
        String baseName = originalName != null ? originalName : "";
        String copyName = baseName + suffix;
        if (copyName.length() > 200) {
            copyName = baseName.substring(0, Math.max(0, baseName.length() - suffix.length())) + suffix;
        }
        return copyName;
    }

    /**
     * 重新映射 Schema 中的页面、组件、视角等 ID，避免复制后 ID 冲突
     */
    private String remapSchemaIds(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            return schemaJson;
        }
        try {
            InsightDashboardSchemaDTO schema = objectMapper.readValue(schemaJson, InsightDashboardSchemaDTO.class);
            Map<String, String> pageIdMap = new HashMap<>();
            Map<String, String> componentIdMap = new HashMap<>();
            Map<String, String> perspectiveIdMap = new HashMap<>();

            if (schema.getPages() != null) {
                for (InsightDashboardSchemaDTO.Page page : schema.getPages()) {
                    String oldPageId = page.getId();
                    String newPageId = generateSchemaId("page");
                    pageIdMap.put(oldPageId, newPageId);
                    page.setId(newPageId);

                    if (page.getComponents() != null) {
                        for (InsightDashboardSchemaDTO.Component component : page.getComponents()) {
                            remapComponentIds(component, componentIdMap);
                        }
                    }
                }
            }

            if (schema.getComponents() != null) {
                for (InsightDashboardSchemaDTO.Component component : schema.getComponents()) {
                    remapComponentIds(component, componentIdMap);
                }
            }

            if (schema.getPerspectives() != null) {
                for (InsightDashboardSchemaDTO.Perspective perspective : schema.getPerspectives()) {
                    String oldPerspectiveId = perspective.getId();
                    String newPerspectiveId = generateSchemaId("persp");
                    perspectiveIdMap.put(oldPerspectiveId, newPerspectiveId);
                    perspective.setId(newPerspectiveId);
                }
            }

            updateSchemaReferences(schema, pageIdMap, componentIdMap, perspectiveIdMap);
            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            log.warn("复制仪表盘时 Schema ID 重映射失败，保留原始 Schema: {}", e.getMessage());
            return schemaJson;
        }
    }

    /**
     * 重新映射单个组件及其 Tab 的 ID
     */
    private void remapComponentIds(InsightDashboardSchemaDTO.Component component, Map<String, String> componentIdMap) {
        String oldComponentId = component.getId();
        String newComponentId = generateSchemaId("comp");
        componentIdMap.put(oldComponentId, newComponentId);
        component.setId(newComponentId);

        if (component.getTabs() != null) {
            for (InsightDashboardSchemaDTO.Tab tab : component.getTabs()) {
                tab.setId(generateSchemaId("tab"));
            }
        }
    }

    /**
     * 更新 Schema 中页面 parentId、组件 boundFilterIds 与 perspectiveIds 等引用
     */
    private void updateSchemaReferences(InsightDashboardSchemaDTO schema,
                                        Map<String, String> pageIdMap,
                                        Map<String, String> componentIdMap,
                                        Map<String, String> perspectiveIdMap) {
        if (schema.getPages() != null) {
            for (InsightDashboardSchemaDTO.Page page : schema.getPages()) {
                if (page.getParentId() != null && !page.getParentId().isBlank()) {
                    page.setParentId(pageIdMap.getOrDefault(page.getParentId(), page.getParentId()));
                }
                if (page.getComponents() != null) {
                    for (InsightDashboardSchemaDTO.Component component : page.getComponents()) {
                        updateComponentReferences(component, componentIdMap, perspectiveIdMap);
                    }
                }
            }
        }
        if (schema.getComponents() != null) {
            for (InsightDashboardSchemaDTO.Component component : schema.getComponents()) {
                updateComponentReferences(component, componentIdMap, perspectiveIdMap);
            }
        }
    }

    /**
     * 更新组件内部的 ID 引用（boundFilterIds、perspectiveIds）
     */
    private void updateComponentReferences(InsightDashboardSchemaDTO.Component component,
                                           Map<String, String> componentIdMap,
                                           Map<String, String> perspectiveIdMap) {
        if (component.getBoundFilterIds() != null) {
            List<String> newBoundFilterIds = component.getBoundFilterIds().stream()
                    .map(oldId -> componentIdMap.getOrDefault(oldId, oldId))
                    .collect(Collectors.toList());
            component.setBoundFilterIds(newBoundFilterIds);
        }
        if (component.getPerspectiveIds() != null) {
            List<String> newPerspectiveIds = component.getPerspectiveIds().stream()
                    .map(oldId -> perspectiveIdMap.getOrDefault(oldId, oldId))
                    .collect(Collectors.toList());
            component.setPerspectiveIds(newPerspectiveIds);
        }
    }

    /**
     * 生成 Schema 元素唯一 ID
     */
    private String generateSchemaId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    @Override
    public SseEmitter streamAiChatDashboard(InsightDashboardAiChatRequest request) {
        SseEmitter emitter = new Utf8SseEmitter(SSE_TIMEOUT_MS);
        AtomicBoolean emitterDone = new AtomicBoolean(false);

        registerEmitterCallbacks(emitter, emitterDone);

        // 启动SSE心跳：在LLM思考/指标检索等空闲阶段周期性发送ping事件，
        // 防止中间代理（nginx/网关，空闲超时通常60s）因连接空闲而掐断流。
        ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (emitterDone.get()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name("ping").data(""));
            } catch (Exception e) {
                // 连接已断或发送失败，停止心跳（emitter的onError会接管清理）
                log.debug("SSE心跳发送失败，停止心跳: {}", e.getMessage());
            }
        }, SSE_HEARTBEAT_INTERVAL_MS, SSE_HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);

        // 在请求线程中捕获用户上下文，异步线程中恢复（ThreadLocal无法跨线程传递）
        UserContext userContext = UserContextHolder.get();

        aiChatExecutor.execute(() -> {
            UserContextHolder.set(userContext);
            try {
                if (request.getDashboardId() != null && !request.getDashboardId().isBlank()) {
                    streamModifyDashboard(emitter, emitterDone, request, userContext);
                } else {
                    streamGenerateDashboard(emitter, emitterDone, request, userContext);
                }
            } catch (Exception e) {
                log.error("AI助手对话失败: {}", e.getMessage(), e);
                if (!emitterDone.get()) {
                    sendSseEvent(emitter, "error", e.getMessage() != null ? e.getMessage() : "AI助手对话失败");
                    completeEmitterQuietly(emitter, emitterDone);
                }
            } finally {
                heartbeat.cancel(false);
                UserContextHolder.clear();
            }
        });

        return emitter;
    }

    /**
     * 流式生成仪表盘
     */
    private void streamGenerateDashboard(SseEmitter emitter, AtomicBoolean emitterDone,
                                          InsightDashboardAiChatRequest request,
                                          UserContext userContext) {
        // 参数校验
        if (request.getDatasourceId() == null) {
            throw new BusinessException(400, "数据源ID不能为空");
        }
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            throw new BusinessException(400, "需求描述不能为空");
        }

        Long datasourceId = request.getDatasourceId();

        // 查询数据源信息
        DatasourceVO datasource = datasourceManageService.getDatasource(datasourceId);
        if (datasource == null) {
            throw new BusinessException(404, "数据源不存在: " + datasourceId);
        }

        // 获取数据源可用指标/维度信息（未建语义层时自动回退表结构检索）
        String schemaContext = buildMetricContext(datasourceId, request.getMessage());

        // 构建系统提示词（格式化后）
        String systemPrompt = String.format(GENERATE_SYSTEM_PROMPT,
                schemaContext,
                request.getMessage(),
                request.getDatasourceId());

        // 流式调用AgentScope
        String conversationId = (request.getConversationId() != null && !request.getConversationId().isBlank())
                ? request.getConversationId()
                : DataAgentConstants.INSIGHT_AI_CHAT_CONVERSATION_PREFIX + UUID.randomUUID();
        AgentCallRequest callRequest = new AgentCallRequest();
        callRequest.setMessage(request.getMessage());
        callRequest.setSystemPrompt(systemPrompt);
        callRequest.setSessionId(conversationId);
        callRequest.setHistoryMessages(convertHistoryMessages(request.getHistoryMessages()));

        Flux<Event> eventFlux = agentScopeService.streamCall(callRequest);
        AtomicReference<String> lastAgentResult = new AtomicReference<>("");

        // 发送 conversationId 给前端，用于下一轮对话保持会话上下文
        try {
            emitter.send(SseEmitter.event().name("conversation_id").data(conversationId));
        } catch (Exception e) {
            log.warn("发送 conversationId 失败：{}", e.getMessage());
        }


        Disposable disposable = subscribeAgentScopeStream(
                eventFlux, emitter, emitterDone, lastAgentResult,
                () -> {
                    // 流式完成，解析最后一轮Agent结果并创建仪表盘
                    String llmResponse = lastAgentResult.get();
                    log.info("AI生成仪表盘：LLM最终响应长度={}, 前200字符={}", llmResponse.length(),
                            llmResponse.length() > 200 ? llmResponse.substring(0, 200) : llmResponse);
                    String schemaJson = parseLlmResponse(llmResponse);

                    InsightDashboardCreateRequest createRequest = new InsightDashboardCreateRequest();
                    createRequest.setName(request.getName());
                    createRequest.setDescription(request.getMessage());
                    createRequest.setSchemaJson(schemaJson);
                    InsightDashboardVO result = createDashboard(createRequest);

                    sendSseEvent(emitter, "result", objectMapper.writeValueAsString(result));
                },
                userContext);

        emitter.onCompletion(disposable::dispose);
        emitter.onTimeout(disposable::dispose);
        emitter.onError(e -> disposable.dispose());
    }

    /**
     * 流式修改仪表盘
     */
    private void streamModifyDashboard(SseEmitter emitter, AtomicBoolean emitterDone,
                                        InsightDashboardAiChatRequest request,
                                        UserContext userContext) {
        Long dashboardId = Long.valueOf(request.getDashboardId().trim());

        // 参数校验
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            throw new BusinessException(400, "修改指令不能为空");
        }

        // 获取现有仪表盘
        requireOwnership(dashboardId);
        InsightDashboardEntity entity = insightDashboardMapper.selectById(dashboardId);

        // 解析现有Schema
        InsightDashboardSchemaDTO currentSchema;
        try {
            currentSchema = objectMapper.readValue(entity.getSchemaJson(), InsightDashboardSchemaDTO.class);
        } catch (Exception e) {
            throw new BusinessException(500, "当前仪表盘Schema解析失败，无法进行AI修改");
        }

        // 构建轻量组件清单（避免把整个 Schema 塞进 prompt，省 token 且 LLM 更聚焦）
        String componentList = buildComponentList(currentSchema);

        // 从现有Schema中提取所有datasourceId，构建数据源可用指标/维度上下文（供 add/update 使用）
        Set<String> datasourceIds = extractDatasourceIds(currentSchema);
        String datasourceContext = buildDatasourceFieldContext(datasourceIds, request.getMessage());

        // 构建修改系统提示词（增量操作指令模式）
        String systemPrompt = String.format(MODIFY_SYSTEM_PROMPT, componentList, datasourceContext, request.getMessage());

        // 流式调用AgentScope
        String conversationId = (request.getConversationId() != null && !request.getConversationId().isBlank())
                ? request.getConversationId()
                : DataAgentConstants.INSIGHT_AI_CHAT_CONVERSATION_PREFIX + UUID.randomUUID();
        AgentCallRequest callRequest = new AgentCallRequest();
        callRequest.setMessage(request.getMessage());
        callRequest.setSystemPrompt(systemPrompt);
        callRequest.setSessionId(conversationId);
        callRequest.setHistoryMessages(convertHistoryMessages(request.getHistoryMessages()));

        Flux<Event> eventFlux = agentScopeService.streamCall(callRequest);
        AtomicReference<String> lastAgentResult = new AtomicReference<>("");

        // 发送 conversationId 给前端，用于下一轮对话保持会话上下文
        try {
            emitter.send(SseEmitter.event().name("conversation_id").data(conversationId));
        } catch (Exception e) {
            log.warn("发送 conversationId 失败：{}", e.getMessage());
        }


        Disposable disposable = subscribeAgentScopeStream(
                eventFlux, emitter, emitterDone, lastAgentResult,
                () -> {
                    // 流式完成，解析操作指令列表并应用到现有 Schema
                    String llmResponse = lastAgentResult.get();
                    log.info("AI修改仪表盘：LLM最终响应长度={}, 前200字符={}", llmResponse.length(),
                            llmResponse.length() > 200 ? llmResponse.substring(0, 200) : llmResponse);
                    List<InsightDashboardPatchOp> ops = parsePatchOps(llmResponse);
                    applyPatchOps(currentSchema, ops);
                    // 补全布局（add 操作可能缺 position）
                    patchComponentLayout(currentSchema);
                    String schemaJson = objectMapper.writeValueAsString(currentSchema);

                    InsightDashboardUpdateRequest updateRequest = new InsightDashboardUpdateRequest();
                    updateRequest.setSchemaJson(schemaJson);
                    InsightDashboardVO result = updateDashboard(dashboardId, updateRequest);

                    sendSseEvent(emitter, "result", objectMapper.writeValueAsString(result));
                },
                userContext);

        emitter.onCompletion(disposable::dispose);
        emitter.onTimeout(disposable::dispose);
        emitter.onError(e -> disposable.dispose());
    }

    /**
     * 订阅 AgentScope 流式事件，统一处理事件分发与 SSE 推送。
     * <p>
     * 参考 test.java 的 ContentBlock 遍历模式：不再使用 getTextContent() 粗粒度提取文本，
     * 而是遍历 msg.getContent() 逐块处理，区分 TextBlock / ThinkingBlock / ToolUseBlock / ToolResultBlock，
     * 并利用 event.isLast() 区分增量推送与最终结果保存。
     * <p>
     * 生成和修改路径共用此方法，差异业务逻辑通过 onComplete 回调注入。
     *
     * @param eventFlux       AgentScope 事件流
     * @param emitter         SSE Emitter
     * @param emitterDone     发射器完成标记
     * @param lastAgentResult 用于保存最后一轮 AGENT_RESULT 文本内容的引用
     * @param onComplete      流式完成后的业务回调（生成/修改仪表盘的具体逻辑）
     * @param userContext     用户上下文（用于 onComplete 中恢复）
     * @return Disposable 订阅句柄
     */
    private Disposable subscribeAgentScopeStream(
            Flux<Event> eventFlux,
            SseEmitter emitter,
            AtomicBoolean emitterDone,
            AtomicReference<String> lastAgentResult,
            ThrowingRunnable onComplete,
            UserContext userContext) {

        return eventFlux.subscribe(
                event -> {
                    if (emitterDone.get()) {
                        return;
                    }
                    handleAgentScopeEvent(event, emitter, emitterDone, lastAgentResult);
                },
                error -> {
                    log.error("AI 助手流式调用失败：{}", error.getMessage());
                    if (!emitterDone.compareAndSet(false, true)) {
                        return;
                    }
                    sendSseEvent(emitter, "error", error.getMessage() != null ? error.getMessage() : "AI 助手失败");
                    completeEmitterQuietly(emitter, emitterDone);
                },
                () -> {
                    if (!emitterDone.compareAndSet(false, true)) {
                        return;
                    }
                    // 执行业务回调
                    UserContextHolder.set(userContext);
                    try {
                        onComplete.run();
                    } catch (BusinessException e) {
                        log.error("AI 助手业务处理失败：{}", e.getMessage());
                        sendSseEvent(emitter, "error", e.getMessage() != null ? e.getMessage() : "AI 助手处理失败");
                    } catch (Exception e) {
                        log.error("AI 助手结果处理失败：{}", e.getMessage());
                        sendSseEvent(emitter, "error", "AI 助手处理失败：无法解析结果");
                    } finally {
                        UserContextHolder.clear();
                        completeEmitterQuietly(emitter, emitterDone);
                    }
                }
        );
    }

    /**
     * 处理单个 AgentScope Event，基于 ContentBlock 遍历分发 SSE 事件。
     * <p>
     * 事件分发策略：
     * <ul>
     *   <li>REASONING / SUMMARY 事件：只推送 ThinkingBlock（真正的思考过程）→ reasoning SSE 事件，
     *       ToolUseBlock → tool_call SSE 事件；
     *       TextBlock 是 LLM 的 JSON 输出，不推送给前端</li>
     *   <li>AGENT_RESULT 事件：LLM返回的是JSON（仪表盘Schema或操作指令），直接更新到数据库，
     *       不推送给前端，仅在 isLast 时保存完整文本用于后续解析</li>
     *   <li>TOOL_RESULT 事件：ToolResultBlock → tool_result SSE 事件（仅在 isLast 时处理）</li>
     *   <li>HINT 事件 → hint SSE 事件</li>
     * </ul>
     * 利用 isLast() 区分增量 delta（推送）和最终完整结果（保存到 lastAgentResult）。
     *
     * @param event           AgentScope 事件
     * @param emitter         SSE Emitter
     * @param emitterDone     发射器完成标记
     * @param lastAgentResult 用于保存最后一轮 AGENT_RESULT 文本内容的引用
     */
    private void handleAgentScopeEvent(Event event, SseEmitter emitter,
                                       AtomicBoolean emitterDone,
                                       AtomicReference<String> lastAgentResult) {
        Msg msg = event.getMessage();
        if (msg == null) {
            return;
        }
        EventType type = event.getType();
        List<ContentBlock> blocks = msg.getContent();
        if (blocks == null || blocks.isEmpty()) {
            return;
        }

        log.debug("[AI 助手] event: type={}, isLast={}, blocks={}, data={}", type, event.isLast(), blocks.size(), JSONUtil.toJsonStr(blocks));

        if (type == EventType.REASONING || type == EventType.SUMMARY) {
            // 推理/总结事件：只推送 ThinkingBlock（真正的思考过程）给前端展示
            // TextBlock 是 LLM 的 JSON 输出（仪表盘Schema或操作指令），不推送给前端，由 AGENT_RESULT 保存后直接更新到数据库
            // isLast=true 时是完整推理结果，与增量块内容重复，跳过避免前端重复拼接
            if (!event.isLast()) {
                for (ContentBlock block : blocks) {
                    if (block instanceof ThinkingBlock thinkingBlock) {
                        String thinking = thinkingBlock.getThinking();
                        if (thinking != null && !thinking.isEmpty()) {
                            sendSseEvent(emitter, "reasoning", thinking);
                        }
                    } else if (block instanceof ToolUseBlock toolUse) {
                        // 推理阶段也可能触发工具调用，推送工具调用信息
                        emitToolCallSse(emitter, toolUse);
                    }
                }
            }
        } else if (type == EventType.AGENT_RESULT) {
            // 最终结果事件：LLM返回的是JSON（仪表盘Schema或操作指令），直接更新到数据库，不推送给前端
            // 仅在 isLast 时保存完整文本用于后续解析
            if (event.isLast()) {
                lastAgentResult.set(msg.getTextContent());
            }
        } else if (type == EventType.TOOL_RESULT && event.isLast()) {
            // 工具结果事件：仅在 isLast 时处理（工具执行完成）
            for (ContentBlock block : blocks) {
                if (block instanceof ToolResultBlock toolResult) {
                    String result = extractToolResultText(toolResult);
                    if (result != null && !result.isEmpty()) {
                        sendSseEvent(emitter, "tool_result", result);
                    }
                }
            }
        } else if (type == EventType.HINT) {
            // RAG/记忆/规划系统的提示信息
            String text = msg.getTextContent();
            if (text != null && !text.isEmpty()) {
                sendSseEvent(emitter, "hint", text);
            }
        }
    }

    /**
     * 推送工具调用 SSE 事件，包含工具名称和参数
     *
     * @param emitter SSE Emitter
     * @param toolUse 工具调用块
     */
    private void emitToolCallSse(SseEmitter emitter, ToolUseBlock toolUse) {
        String toolName = toolUse.getName();
        if (toolName == null || toolName.isBlank()) {
            toolName = "unknown";
        }
        String toolCallId = toolUse.getId();
        if (toolCallId == null) {
            toolCallId = UUID.randomUUID().toString();
        }
        // 推送工具调用开始事件
        StringBuilder sb = new StringBuilder();
        sb.append("{\"toolCallId\":\"").append(toolCallId).append("\"");
        sb.append(",\"toolName\":\"").append(toolName).append("\"");
        String args = toolUse.getContent();
        if (args != null && !args.isEmpty()) {
            sb.append(",\"args\":").append(args);
        }
        sb.append("}");
        sendSseEvent(emitter, "tool_call", sb.toString());
    }

    /**
     * 从 ToolResultBlock 中提取文本结果
     *
     * @param toolResult 工具结果块
     * @return 文本结果
     */
    private String extractToolResultText(ToolResultBlock toolResult) {
        if (toolResult.getOutput() == null || toolResult.getOutput().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : toolResult.getOutput()) {
            if (block instanceof TextBlock textBlock) {
                sb.append(textBlock.getText());
            }
        }
        return sb.toString();
    }

    /**
     * 将请求中的历史消息 DTO 转换为 AgentCallRequest 中的历史消息 DTO。
     *
     * @param historyMessages 请求中的历史消息列表
     * @return AgentCallRequest 格式的历史消息列表，为空时返回 null
     */
    private List<AgentCallRequest.HistoryMessage> convertHistoryMessages(
            List<InsightDashboardAiChatRequest.HistoryMessage> historyMessages) {
        if (historyMessages == null || historyMessages.isEmpty()) {
            return null;
        }
        List<AgentCallRequest.HistoryMessage> result = new ArrayList<>();
        for (InsightDashboardAiChatRequest.HistoryMessage msg : historyMessages) {
            if (msg.getContent() == null || msg.getContent().isBlank()) {
                continue;
            }
            AgentCallRequest.HistoryMessage converted = new AgentCallRequest.HistoryMessage();
            converted.setRole(msg.getRole());
            converted.setContent(msg.getContent());
            result.add(converted);
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * 构建轻量组件清单（id | type | title | position），供修改模式 prompt 使用。
     * 相比塞入完整 Schema，大幅减少 token 且让 LLM 聚焦于组件标识。
     */
    private String buildComponentList(InsightDashboardSchemaDTO schema) {
        StringBuilder sb = new StringBuilder();
        for (InsightDashboardSchemaDTO.Page page : schema.getPages() != null ? schema.getPages() : List.<InsightDashboardSchemaDTO.Page>of()) {
            sb.append("[页面] id=").append(page.getId()).append(", name=").append(page.getName()).append("\n");
            if (page.getComponents() == null) {
                continue;
            }
            for (InsightDashboardSchemaDTO.Component c : page.getComponents()) {
                InsightDashboardSchemaDTO.Position p = c.getPosition();
                String pos = p == null ? "无" : String.format("(x=%d,y=%d,w=%d,h=%d)", p.getX(), p.getY(), p.getW(), p.getH());
                sb.append("  - id=").append(c.getId())
                        .append(" | type=").append(c.getType())
                        .append(" | title=").append(c.getTitle())
                        .append(" | position=").append(pos);
                if (c.getChartType() != null) {
                    sb.append(" | chartType=").append(c.getChartType());
                }
                if (c.getDataSource() != null && c.getDataSource().getMetrics() != null && !c.getDataSource().getMetrics().isEmpty()) {
                    sb.append(" | metrics=").append(c.getDataSource().getMetrics());
                }
                sb.append("\n");
            }
        }
        return sb.isEmpty() ? "（仪表盘无组件）" : sb.toString();
    }

    /**
     * 构建数据源可用指标/维度上下文，供 add/update 操作填写 dataSource。
     * 基于用户修改指令检索相关指标与维度，未建语义层时自动回退表结构检索。
     */
    private String buildDatasourceFieldContext(Set<String> datasourceIds, String query) {
        if (datasourceIds == null || datasourceIds.isEmpty()) {
            return "当前仪表盘未绑定数据源，add 组件时需用户提供 datasourceId";
        }
        StringBuilder context = new StringBuilder();
        for (String dsId : datasourceIds) {
            try {
                Long id = Long.parseLong(dsId);
                context.append(buildMetricContext(id, query));
            } catch (NumberFormatException e) {
                context.append("- 数据源ID: ").append(dsId).append("（格式异常）\n");
            }
        }
        return context.toString();
    }

    /**
     * 解析 LLM 返回的操作指令列表
     */
    private List<InsightDashboardPatchOp> parsePatchOps(String llmResponse) {
        if (llmResponse == null || llmResponse.isBlank()) {
            throw new BusinessException(500, "AI修改失败：LLM返回为空");
        }
        String content = stripMarkdownCodeBlock(llmResponse).trim();
        // LLM 偶尔会漏掉外层数组方括号，输出成 "},{..." 这样的逗号分隔对象序列，
        // 这里做容错规整：非 [ 开头时，提取所有顶层 {...} 对象重新包成数组。
        content = wrapObjectsAsArray(content);
        try {
            List<InsightDashboardPatchOp> ops = objectMapper.readValue(content,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, InsightDashboardPatchOp.class));
            if (ops == null || ops.isEmpty()) {
                throw new BusinessException(500, "AI修改失败：未识别到任何操作指令");
            }
            return ops;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI修改仪表盘：操作指令解析失败, response={}", content, e);
            throw new BusinessException(500, "AI修改失败：无法解析操作指令，请换种方式描述");
        }
    }

    /**
     * 将 LLM 输出规整为 JSON 数组字符串。
     * <p>
     * 处理两种常见偏差：
     * <ul>
     *   <li>漏掉外层方括号，输出为 {@code {op:...},{op:...}} —— 用括号深度匹配提取每个顶层对象，包成 {@code [...]}</li>
     *   <li>外层多包了一层对象，如 {@code {"ops":[...]}} —— 取其首个数组字段</li>
     * </ul>
     * 已是合法数组时原样返回。
     */
    private String wrapObjectsAsArray(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("[")) {
            return trimmed;
        }
        // 提取所有顶层 {...} 对象（按花括号深度匹配，忽略字符串内的花括号）
        List<String> objects = extractTopLevelObjects(trimmed);
        if (objects.isEmpty()) {
            return trimmed;
        }
        return "[" + String.join(",", objects) + "]";
    }

    /**
     * 按花括号深度匹配提取顶层 JSON 对象，忽略字符串字面量内的花括号。
     */
    private List<String> extractTopLevelObjects(String content) {
        List<String> objects = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    objects.add(content.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return objects;
    }

    /**
     * 将操作指令列表逐条应用到 Schema（原地修改）。
     * <p>
     * 采用两阶段应用，保证页面操作先于组件操作执行：
     * <ol>
     *   <li>页面阶段：先应用 add-page / rename-page / delete-page，建立 LLM 占位 pageId
     *       （add-page 的 id 字段）→ 真实页面 id 的映射</li>
     *   <li>组件阶段：将组件操作（add/update/...）中的 pageId 占位符替换为真实 id 后应用</li>
     * </ol>
     * 这样 LLM 在同一条消息里「先 add-page 再往新页面 add 组件」即可正确关联，
     * 无需关心 LLM 的指令顺序。
     */
    private void applyPatchOps(InsightDashboardSchemaDTO schema, List<InsightDashboardPatchOp> ops) {
        if (ops == null || ops.isEmpty()) {
            return;
        }
        Map<String, String> pageIdAlias = new HashMap<>();

        // 阶段1：页面操作
        for (InsightDashboardPatchOp op : ops) {
            if (op == null || op.getOp() == null) {
                continue;
            }
            String opType = op.getOp().trim().toLowerCase();
            if ("add-page".equals(opType)) {
                String aliasId = applyAddPage(schema, op);
                // 若 LLM 为 add-page 提供了占位 id，记录映射供后续 add 引用
                if (op.getId() != null && !op.getId().isBlank() && aliasId != null) {
                    pageIdAlias.put(op.getId().trim(), aliasId);
                }
            } else if ("delete-page".equals(opType)) {
                applyDeletePage(schema, op);
            } else if ("rename-page".equals(opType)) {
                applyRenamePage(schema, op);
            } else if ("move-page".equals(opType)) {
                applyMovePage(schema, op);
            }
        }

        // 阶段2：组件操作（替换占位 pageId）
        for (InsightDashboardPatchOp op : ops) {
            if (op == null || op.getOp() == null) {
                continue;
            }
            String opType = op.getOp().trim().toLowerCase();
            if ("add-page".equals(opType) || "delete-page".equals(opType)
                    || "rename-page".equals(opType)) {
                continue;
            }
            // 替换 add 指令中的占位 pageId
            if (op.getPageId() != null && pageIdAlias.containsKey(op.getPageId().trim())) {
                op.setPageId(pageIdAlias.get(op.getPageId().trim()));
            }
            applySingleOp(schema, op);
        }
    }

    /**
     * 应用单条组件操作指令（页面操作由 {@link #applyPatchOps} 阶段1处理，不在此分发）
     */
    private void applySingleOp(InsightDashboardSchemaDTO schema, InsightDashboardPatchOp op) {
        if (op == null || op.getOp() == null) {
            return;
        }
        String opType = op.getOp().trim().toLowerCase();
        switch (opType) {
            case "resize" -> applyResize(schema, op);
            case "move" -> applyMove(schema, op);
            case "delete" -> applyDelete(schema, op);
            case "add" -> applyAdd(schema, op);
            case "update" -> applyUpdate(schema, op);
            default -> log.warn("AI修改仪表盘：未知操作类型 op={}", opType);
        }
    }

    /**
     * add-page：新增页面。
     *
     * @return 新页面的真实 id（供 {@link #applyPatchOps} 建立占位 id 映射）
     */
    private String applyAddPage(InsightDashboardSchemaDTO schema, InsightDashboardPatchOp op) {
        if (schema.getPages() == null) {
            schema.setPages(new ArrayList<>());
        }
        // 用户未提供 name 时给默认名，避免空标题
        String name = (op.getPageName() != null && !op.getPageName().isBlank())
                ? op.getPageName() : "新页面";

        InsightDashboardSchemaDTO.Page page = new InsightDashboardSchemaDTO.Page();
        page.setId(generateSchemaId("page"));
        page.setName(name);
        page.setIcon(op.getPageIcon());
        page.setParentId(op.getPageParentId());
        page.setOrder(op.getPageOrder());
        page.setComponents(new ArrayList<>());
        schema.getPages().add(page);
        log.info("AI修改 add-page：新增页面 id={}, name={}", page.getId(), name);
        return page.getId();
    }

    /** delete-page：删除页面及其所有组件 */
    private void applyDeletePage(InsightDashboardSchemaDTO schema, InsightDashboardPatchOp op) {
        if (schema.getPages() == null || op.getId() == null) {
            return;
        }
        int before = schema.getPages().size();
        schema.getPages().removeIf(p -> op.getId().equals(p.getId()));
        if (schema.getPages().size() == before) {
            log.warn("AI修改 delete-page：页面不存在 id={}", op.getId());
        }
    }

    /** rename-page：重命名页面（可同时更新 icon/parentId/order） */
    private void applyRenamePage(InsightDashboardSchemaDTO schema, InsightDashboardPatchOp op) {
        if (schema.getPages() == null || op.getId() == null) {
            return;
        }
        InsightDashboardSchemaDTO.Page target = null;
        for (InsightDashboardSchemaDTO.Page p : schema.getPages()) {
            if (op.getId().equals(p.getId())) {
                target = p;
                break;
            }
        }
        if (target == null) {
            log.warn("AI修改 rename-page：页面不存在 id={}", op.getId());
            return;
        }
        if (op.getPageName() != null && !op.getPageName().isBlank()) {
            target.setName(op.getPageName());
        }
        if (op.getPageIcon() != null) {
            target.setIcon(op.getPageIcon());
        }
        if (op.getPageParentId() != null) {
            target.setParentId(op.getPageParentId());
        }
        if (op.getPageOrder() != null) {
            target.setOrder(op.getPageOrder());
        }
    }

    /** move-page：调整页面顺序（通过修改 order 字段实现） */
    private void applyMovePage(InsightDashboardSchemaDTO schema, InsightDashboardPatchOp op) {
        log.debug("AI 修改 move-page：收到 op.id={}, op.pageOrder={}", op.getId(), op.getPageOrder());
        if (schema.getPages() == null || op.getId() == null || op.getPageOrder() == null) {
            return;
        }
        InsightDashboardSchemaDTO.Page target = null;
        for (InsightDashboardSchemaDTO.Page p : schema.getPages()) {
            if (op.getId().equals(p.getId())) {
                target = p;
                break;
            }
        }
        if (target == null) {
            log.warn("AI 修改 move-page：页面不存在 id={}", op.getId());
            return;
        }
        // 计算新旧位置
        int oldIndex = schema.getPages().indexOf(target);
        int newIndex = Math.max(0, Math.min(op.getPageOrder(), schema.getPages().size() - 1));
        
        if (oldIndex == newIndex) {
            return; // 位置未变化
        }
        
        // 从列表中移除并插入到新位置
        schema.getPages().remove(oldIndex);
        schema.getPages().add(newIndex, target);
        
        // 重新排序所有页面的 order 字段（保持连续）
        for (int i = 0; i < schema.getPages().size(); i++) {
            schema.getPages().get(i).setOrder(i);
        }
        log.info("AI 修改 move-page：页面 id={} 从位置 {} 移动到 {}", op.getId(), oldIndex, newIndex);
    }

    /** resize：调整组件 w/h（边界保护） */
    private void applyResize(InsightDashboardSchemaDTO schema, InsightDashboardPatchOp op) {
        InsightDashboardSchemaDTO.Component c = findComponent(schema, op.getId());
        if (c == null) {
            log.warn("AI修改 resize：组件不存在 id={}", op.getId());
            return;
        }
        if (c.getPosition() == null) {
            c.setPosition(new InsightDashboardSchemaDTO.Position());
        }
        InsightDashboardSchemaDTO.Position p = c.getPosition();
        if (op.getW() != null) {
            p.setW(Math.max(1, Math.min(24, op.getW())));
        }
        if (op.getH() != null) {
            p.setH(Math.max(1, Math.min(30, op.getH())));
        }
    }

    /** move：调整组件 x/y（边界保护） */
    private void applyMove(InsightDashboardSchemaDTO schema, InsightDashboardPatchOp op) {
        InsightDashboardSchemaDTO.Component c = findComponent(schema, op.getId());
        if (c == null) {
            log.warn("AI修改 move：组件不存在 id={}", op.getId());
            return;
        }
        if (c.getPosition() == null) {
            c.setPosition(new InsightDashboardSchemaDTO.Position());
        }
        InsightDashboardSchemaDTO.Position p = c.getPosition();
        if (op.getX() != null) {
            p.setX(Math.max(0, Math.min(23, op.getX())));
        }
        if (op.getY() != null) {
            p.setY(Math.max(0, op.getY()));
        }
    }

    /** delete：从所在页面移除组件 */
    private void applyDelete(InsightDashboardSchemaDTO schema, InsightDashboardPatchOp op) {
        if (schema.getPages() == null) {
            return;
        }
        for (InsightDashboardSchemaDTO.Page page : schema.getPages()) {
            if (page.getComponents() != null) {
                page.getComponents().removeIf(c -> c.getId() != null && c.getId().equals(op.getId()));
            }
        }
    }

    /** add：新增组件到目标页面（缺省追加到最后一个页面），position 缺省由 patchComponentLayout 补全 */
    private void applyAdd(InsightDashboardSchemaDTO schema, InsightDashboardPatchOp op) {
        if (op.getType() == null || op.getType().isBlank()) {
            log.warn("AI修改 add：缺少 type");
            return;
        }
        if (schema.getPages() == null || schema.getPages().isEmpty()) {
            log.warn("AI修改 add：Schema 无页面，无法新增");
            return;
        }
        InsightDashboardSchemaDTO.Page targetPage = null;
        if (op.getPageId() != null && !op.getPageId().isBlank()) {
            for (InsightDashboardSchemaDTO.Page p : schema.getPages()) {
                if (op.getPageId().equals(p.getId())) {
                    targetPage = p;
                    break;
                }
            }
        }
        if (targetPage == null) {
            // fallback 到最后一个页面（通常是最新添加的页面），符合「新增页面后加组件」的直觉
            targetPage = schema.getPages().get(schema.getPages().size() - 1);
        }
        if (targetPage.getComponents() == null) {
            targetPage.setComponents(new java.util.ArrayList<>());
        }

        InsightDashboardSchemaDTO.Component c = new InsightDashboardSchemaDTO.Component();
        c.setId(op.getId() != null && !op.getId().isBlank() ? op.getId() : generateSchemaId("comp"));
        c.setType(op.getType());
        c.setTitle(op.getTitle() != null && !op.getTitle().isBlank() ? op.getTitle() : resolveDefaultTitle(op.getType()));
        c.setChartType(op.getChartType());
        c.setRenderType(op.getRenderType() != null && !op.getRenderType().isBlank()
                ? op.getRenderType() : resolveRenderType(op.getType()));
        c.setDataSource(op.getDataSource());
        c.setConfig(op.getConfig());
        c.setTabs(op.getTabs());
        c.setBoundFilterIds(op.getBoundFilterIds());
        c.setPosition(op.getPosition());
        // timeFilter 默认 config
        if ("timeFilter".equals(op.getType()) && c.getConfig() == null) {
            Map<String, Object> cfg = new HashMap<>();
            cfg.put("field", "metric_time");
            cfg.put("availablePresets", List.of("today", "7d", "30d", "90d", "custom"));
            c.setConfig(cfg);
        }
        targetPage.getComponents().add(c);
    }

    /** update：修改组件非 position 属性（title/chartType/dataSource/config/tabs 等） */
    private void applyUpdate(InsightDashboardSchemaDTO schema, InsightDashboardPatchOp op) {
        InsightDashboardSchemaDTO.Component c = findComponent(schema, op.getId());
        if (c == null) {
            log.warn("AI修改 update：组件不存在 id={}", op.getId());
            return;
        }
        if (op.getTitle() != null) {
            c.setTitle(op.getTitle());
        }
        if (op.getChartType() != null) {
            c.setChartType(op.getChartType());
        }
        if (op.getDataSource() != null) {
            c.setDataSource(op.getDataSource());
        }
        if (op.getConfig() != null) {
            c.setConfig(op.getConfig());
        }
        if (op.getRenderType() != null) {
            c.setRenderType(op.getRenderType());
        }
        if (op.getTabs() != null) {
            c.setTabs(op.getTabs());
        }
        if (op.getBoundFilterIds() != null) {
            c.setBoundFilterIds(op.getBoundFilterIds());
        }
    }

    /** 在所有页面中按 ID 查找组件 */
    private InsightDashboardSchemaDTO.Component findComponent(InsightDashboardSchemaDTO schema, String id) {
        if (id == null || schema.getPages() == null) {
            return null;
        }
        for (InsightDashboardSchemaDTO.Page page : schema.getPages()) {
            if (page.getComponents() == null) {
                continue;
            }
            for (InsightDashboardSchemaDTO.Component c : page.getComponents()) {
                if (id.equals(c.getId())) {
                    return c;
                }
            }
        }
        return null;
    }

    /** 根据组件类型推导默认标题 */
    private String resolveDefaultTitle(String type) {
        if (type == null) {
            return "组件";
        }
        return switch (type) {
            case "kpi" -> "KPI指标";
            case "chart" -> "图表";
            case "table" -> "数据表格";
            case "filter" -> "筛选器";
            case "timeFilter" -> "时间筛选";
            case "aiAnalysis" -> "AI分析";
            default -> "组件";
        };
    }

    /**
     * 从仪表盘Schema中提取所有数据源ID
     */
    private Set<String> extractDatasourceIds(InsightDashboardSchemaDTO schema) {
        Set<String> datasourceIds = new LinkedHashSet<>();
        List<InsightDashboardSchemaDTO.Component> allComponents = schema.getAllComponents();
        for (InsightDashboardSchemaDTO.Component component : allComponents) {
            if (component.getDataSource() != null && component.getDataSource().getDatasourceId() != null
                    && !component.getDataSource().getDatasourceId().isBlank()) {
                datasourceIds.add(component.getDataSource().getDatasourceId());
            }
        }
        return datasourceIds;
    }

    /**
     * 构建数据源指标上下文信息
     * <p>
     * 仪表盘组件数据模型本就是指标驱动的（{@code dataSource.metrics/dimensions}），
     * 因此 AI 生成/修改时应优先检索可用指标与维度，而非表结构。
     * 优先调用 AlouData 语义层指标+维度混合检索；若该数据源未建语义层或无命中
     * （metricHits 与 dimensionHits 均为空），回退到 {@link #buildSchemaContext} 表结构检索，
     * 保证未建语义层的数据源仍可生成仪表盘。
     */
    private String buildMetricContext(Long datasourceId, String query) {
        AloudataSearchResult searchResult = aloudataSemanticEsService.hybridSearch(
                datasourceId, query,
                DataAgentConstants.INSIGHT_GENERATE_SCHEMA_TOP_K,
                DataAgentConstants.SCHEMA_SEARCH_DEFAULT_THRESHOLD);

        List<AloudataSearchResult.MetricHit> metricHits = searchResult.getMetricHits();
        List<AloudataSearchResult.DimensionHit> dimensionHits = searchResult.getDimensionHits();
        boolean hasMetric = metricHits != null && !metricHits.isEmpty();
        boolean hasDimension = dimensionHits != null && !dimensionHits.isEmpty();

        // 指标和维度均无命中：该数据源可能未建 AlouData 语义层，回退表结构检索
        if (!hasMetric && !hasDimension) {
            return buildSchemaContext(datasourceId, query);
        }

        StringBuilder context = new StringBuilder();
        if (hasMetric) {
            context.append("可用指标（基于语义检索，metrics 必须使用下列指标英文名）：\n");
            for (AloudataSearchResult.MetricHit hit : metricHits) {
                context.append("- ").append(hit.getPromptInfo()).append("\n");
            }
        }
        if (hasDimension) {
            context.append("\n可用维度（dimensions 必须使用下列维度英文名）：\n");
            for (AloudataSearchResult.DimensionHit hit : dimensionHits) {
                context.append("- ").append(hit.getPromptInfo()).append("\n");
            }
        }
        return context.toString();
    }

    /**
     * 构建数据源Schema上下文信息
     */
    private String buildSchemaContext(Long datasourceId, String userDescription) {
        StringBuilder context = new StringBuilder();

        SchemaSearchRequest searchRequest = new SchemaSearchRequest();
        searchRequest.setDatasourceId(datasourceId);
        searchRequest.setQuery(userDescription);
        searchRequest.setTopK(DataAgentConstants.INSIGHT_GENERATE_SCHEMA_TOP_K);
        searchRequest.setSimilarityThreshold(DataAgentConstants.SCHEMA_SEARCH_DEFAULT_THRESHOLD);

        SchemaSearchResult searchResult = schemaEmbeddingService.searchSchema(searchRequest);

        if (searchResult.getTableHits() != null && !searchResult.getTableHits().isEmpty()) {
            context.append("相关数据表（基于语义检索）：\n");
            for (SchemaSearchResult.TableHit hit : searchResult.getTableHits()) {
                appendTableInfo(context, datasourceId, hit.getTableName(), hit.getTableComment(), hit.getSemanticFields());
            }
        } else {
            List<DatasourceTableVO> tables = datasourceManageService.listTables(datasourceId);
            if (tables == null || tables.isEmpty()) {
                context.append("数据源下暂无表结构信息\n");
            } else {
                context.append("数据表列表：\n");
                for (DatasourceTableVO table : tables) {
                    appendTableInfo(context, datasourceId, table.getTableName(), table.getTableComment(), null);
                }
            }
        }

        if (searchResult.getRelations() != null && !searchResult.getRelations().isEmpty()) {
            context.append("\n表间关联关系：\n");
            for (LogicalRelationVO relation : searchResult.getRelations()) {
                context.append("- ").append(relation.getPromptInfo()).append("\n");
            }
        }

        return context.toString();
    }

    /**
     * 追加单张表的信息到上下文
     */
    private void appendTableInfo(StringBuilder context, Long datasourceId,
                                 String tableName, String tableComment,
                                 List<SemanticModelVO> semanticFields) {
        context.append("- 表名: ").append(tableName);
        if (tableComment != null && !tableComment.isBlank()) {
            context.append(" (").append(tableComment).append(")");
        }
        context.append("\n");

        if (semanticFields != null && !semanticFields.isEmpty()) {
            context.append("  字段: ");
            for (SemanticModelVO field : semanticFields) {
                context.append(field.getPromptInfo()).append("; ");
            }
            context.append("\n");
        } else {
            List<DatasourceTableVO> tables = datasourceManageService.listTables(datasourceId);
            DatasourceTableVO matchedTable = null;
            for (DatasourceTableVO t : tables) {
                if (tableName.equals(t.getTableName())) {
                    matchedTable = t;
                    break;
                }
            }
            if (matchedTable != null && matchedTable.getId() != null) {
                DatasourceTableVO detail = datasourceManageService.getTableDetail(datasourceId, matchedTable.getId());
                if (detail != null && detail.getColumns() != null) {
                    context.append("  字段: ");
                    for (DatasourceColumnVO col : detail.getColumns()) {
                        context.append(col.getColumnName());
                        if (col.getDataType() != null) {
                            context.append("(").append(col.getDataType()).append(")");
                        }
                        if (col.getColumnComment() != null && !col.getColumnComment().isBlank()) {
                            context.append("-").append(col.getColumnComment());
                        }
                        context.append("; ");
                    }
                    context.append("\n");
                }
            }
        }
    }

    /**
     * 解析LLM返回的JSON
     */
    private String parseLlmResponse(String llmResponse) {
        if (llmResponse == null || llmResponse.isBlank()) {
            throw new BusinessException(500, "AI生成失败：LLM返回为空");
        }

        String content = llmResponse.trim();
        content = stripMarkdownCodeBlock(content);

        try {
            InsightDashboardSchemaDTO schema = objectMapper.readValue(content, InsightDashboardSchemaDTO.class);
            patchComponentLayout(schema);
            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            log.warn("AI生成仪表盘：LLM返回的JSON解析失败, response={}", content, e);
            throw new BusinessException(500, "AI生成失败：无法解析生成的仪表盘Schema");
        }
    }

    /**
     * 去除LLM输出中的markdown代码块包裹
     */
    private String stripMarkdownCodeBlock(String content) {
        if (content.contains("```json")) {
            int start = content.indexOf("```json");
            int end = content.lastIndexOf("```");
            if (start >= 0 && end > start + 6) {
                content = content.substring(start + 7, end).trim();
            }
        } else if (content.contains("```")) {
            int start = content.indexOf("```");
            int end = content.lastIndexOf("```");
            if (start >= 0 && end > start + 2) {
                content = content.substring(start + 3, end).trim();
                if (content.contains("\n")) {
                    String firstLine = content.substring(0, content.indexOf("\n")).trim();
                    if (firstLine.matches("[a-zA-Z]+")) {
                        content = content.substring(content.indexOf("\n") + 1).trim();
                    }
                }
            }
        }

        int firstBrace = content.indexOf('{');
        int lastBrace = content.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            content = content.substring(firstBrace, lastBrace + 1);
        }

        return content;
    }

    /**
     * 补全组件布局信息
     */
    private void patchComponentLayout(InsightDashboardSchemaDTO schema) {
        if (schema.getPages() == null || schema.getPages().isEmpty()) {
            return;
        }

        int componentIndex = 0;
        for (InsightDashboardSchemaDTO.Page page : schema.getPages()) {
            if (page.getId() == null || page.getId().isBlank()) {
                page.setId("page_" + schema.getPages().indexOf(page));
            }
            if (page.getOrder() == null) {
                page.setOrder(schema.getPages().indexOf(page));
            }

            if (page.getComponents() == null || page.getComponents().isEmpty()) {
                continue;
            }

            for (InsightDashboardSchemaDTO.Component component : page.getComponents()) {
                if (component.getId() == null || component.getId().isBlank()) {
                    component.setId("comp_" + componentIndex);
                }
                componentIndex++;

                if (component.getPosition() == null) {
                    component.setPosition(new InsightDashboardSchemaDTO.Position());
                }
                InsightDashboardSchemaDTO.Position pos = component.getPosition();
                int col = componentIndex % DataAgentConstants.INSIGHT_GENERATE_COLUMNS_PER_ROW;
                int row = componentIndex / DataAgentConstants.INSIGHT_GENERATE_COLUMNS_PER_ROW;
                if (pos.getX() == null) {
                    pos.setX(col * DataAgentConstants.INSIGHT_GENERATE_DEFAULT_COMPONENT_W);
                }
                if (pos.getY() == null) {
                    pos.setY(row * DataAgentConstants.INSIGHT_GENERATE_DEFAULT_COMPONENT_H);
                }
                if (pos.getW() == null || pos.getW() == 0) {
                    pos.setW(DataAgentConstants.INSIGHT_GENERATE_DEFAULT_COMPONENT_W);
                }
                if (pos.getH() == null || pos.getH() == 0) {
                    pos.setH(DataAgentConstants.INSIGHT_GENERATE_DEFAULT_COMPONENT_H);
                }

                if (component.getRenderType() == null || component.getRenderType().isBlank()) {
                    String renderType = resolveRenderType(component.getType());
                    if (renderType != null) {
                        component.setRenderType(renderType);
                    }
                }

                if ("chart".equals(component.getType()) && (component.getChartType() == null || component.getChartType().isBlank())) {
                    component.setChartType(DataAgentConstants.CHART_TYPE_BAR);
                }

                if (component.getDataSource() != null && (component.getDataSource().getDatasourceId() == null || component.getDataSource().getDatasourceId().isBlank())) {
                    log.warn("AI生成仪表盘：组件 {} 的dataSource缺少datasourceId", component.getId());
                }
            }
        }
    }

    /**
     * 根据组件类型解析渲染类型
     */
    private String resolveRenderType(String type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case "chart" -> DataAgentConstants.INSIGHT_RENDER_TYPE_ECHARTS;
            case "kpi" -> DataAgentConstants.INSIGHT_RENDER_TYPE_KPI;
            case "table" -> DataAgentConstants.INSIGHT_RENDER_TYPE_TABLE;
            default -> null;
        };
    }

    /**
     * 发送SSE事件
     */
    private void sendSseEvent(SseEmitter emitter, String eventName, String data) {
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (Exception e) {
            log.debug("SSE发送事件失败: event={}, error={}", eventName, e.getMessage());
        }
    }

    /**
     * 注册SSE Emitter回调
     */
    private void registerEmitterCallbacks(SseEmitter emitter, AtomicBoolean emitterDone) {
        emitter.onCompletion(() -> log.debug("AI助手SSE emitter completed"));
        emitter.onTimeout(() -> {
            log.debug("AI助手SSE emitter timeout");
            completeEmitterQuietly(emitter, emitterDone);
        });
        emitter.onError(e -> {
            log.debug("AI助手SSE emitter error: {}", e.getMessage());
            completeEmitterQuietly(emitter, emitterDone);
        });
    }

    /**
     * 安全关闭SSE Emitter
     */
    private void completeEmitterQuietly(SseEmitter emitter, AtomicBoolean emitterDone) {
        if (!emitterDone.compareAndSet(false, true)) {
            return;
        }
        try {
            emitter.complete();
        } catch (Exception e) {
            log.debug("SSE Emitter already completed: {}", e.getMessage());
        }
    }

    /**
     * 写操作归属校验：存在性 + workspaceId 一致性 + 归属
     * （仅创建者本人 或 工作区 admin/owner 可修改/删除；历史无主数据仅管理员层级可维护）。
     */
    private void requireOwnership(Long id) {
        InsightDashboardEntity entity = insightDashboardMapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(404, "仪表盘不存在: " + id);
        }
        Long currentWorkspaceId = workspaceGuard.currentWorkspaceId();
        if (entity.getWorkspaceId() == null
                || !entity.getWorkspaceId().equals(currentWorkspaceId)) {
            throw new BusinessException(403, "无权访问该仪表盘");
        }
        workspaceGuard.requireResourceOwner(entity.getOwnerId());
    }

    private InsightDashboardVO toVO(InsightDashboardEntity entity) {
        InsightDashboardVO vo = new InsightDashboardVO();
        BeanUtils.copyProperties(entity, vo);
        if (entity.getCreateTime() != null) {
            vo.setCreateTime(entity.getCreateTime().toString());
        }
        if (entity.getUpdateTime() != null) {
            vo.setUpdateTime(entity.getUpdateTime().toString());
        }
        return vo;
    }

    /**
     * 可抛出异常的 Runnable 函数式接口，用于 onComplete 回调中允许抛出 checked exception
     */
    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
