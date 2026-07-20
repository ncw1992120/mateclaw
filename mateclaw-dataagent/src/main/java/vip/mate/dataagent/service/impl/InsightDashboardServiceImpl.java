package vip.mate.dataagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.Event;
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
import vip.mate.dataagent.service.DatasourceManageService;
import vip.mate.dataagent.service.InsightDashboardService;
import vip.mate.dataagent.service.SchemaEmbeddingService;
import vip.mate.dataagent.support.Utf8SseEmitter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
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
    private final SchemaEmbeddingService schemaEmbeddingService;
    private final ObjectMapper objectMapper;
    private final AgentScopeService agentScopeService;
    private final ExecutorService aiChatExecutor;

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
                      "position": {"x": 0, "y": 0, "w": 6, "h": 4},
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
            6. position说明：栅格布局，总宽度12列。w=6表示半宽，w=12表示全宽。h=4为默认高度。
            7. 组件布局要求：每行放2个组件（w=6），从上到下依次排列，y值递增
            8. metrics和dimensions使用数据源中的实际字段名（优先使用语义模型中的business_name）
            9. 根据用户需求合理选择图表类型：
               - 趋势分析 -> line/area
               - 对比分析 -> bar
               - 占比分布 -> pie
               - 关键指标 -> kpi
               - 明细数据 -> table
            10. 每个仪表盘建议包含3-8个组件，覆盖用户需求的不同方面
            11. 如果用户需求涉及筛选，添加filter或timeFilter组件
            """;

    /** AI助手修改仪表盘的系统提示词 */
    private static final String MODIFY_SYSTEM_PROMPT = """
            你是一个数据可视化专家。你的任务是根据用户的修改指令，修改已有的仪表盘Schema JSON。

            ## 当前仪表盘Schema
            %s

            ## 数据源信息
            %s

            ## 用户修改指令
            %s

            ## 要求
            1. 严格返回完整的修改后的JSON，不要包含任何解释性文字
            2. 保持原有的Schema结构不变，只修改用户要求的部分
            3. 如果用户要求添加组件，新组件需要完整的position、dataSource等配置
            4. 如果用户要求删除组件，直接从components数组中移除
            5. 如果用户要求修改组件，只修改对应组件的属性
            6. 组件类型(type)说明：chart(图表)、kpi(KPI卡片)、table(表格)、filter(筛选器)、timeFilter(时间筛选)
            7. 图表子类型(chartType)可选值：line、bar、pie、area、scatter、radar
            8. renderType对应关系：chart→echarts、kpi→kpi、table→table
            9. position说明：栅格布局，总宽度12列。w=6半宽，w=12全宽
            10. 新增组件的ID格式：comp_数字
            """;

    /** SSE超时时间（10分钟） */
    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L;

    public InsightDashboardServiceImpl(InsightDashboardMapper insightDashboardMapper,
                                       WorkspaceGuard workspaceGuard,
                                       DatasourceManageService datasourceManageService,
                                       SchemaEmbeddingService schemaEmbeddingService,
                                       ObjectMapper objectMapper,
                                       AgentScopeService agentScopeService) {
        this.insightDashboardMapper = insightDashboardMapper;
        this.workspaceGuard = workspaceGuard;
        this.datasourceManageService = datasourceManageService;
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
    public SseEmitter streamAiChatDashboard(InsightDashboardAiChatRequest request) {
        SseEmitter emitter = new Utf8SseEmitter(SSE_TIMEOUT_MS);
        AtomicBoolean emitterDone = new AtomicBoolean(false);

        registerEmitterCallbacks(emitter, emitterDone);

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

        // 获取数据源Schema信息
        String schemaContext = buildSchemaContext(datasourceId, request.getMessage());

        // 构建系统提示词（格式化后）
        String systemPrompt = String.format(GENERATE_SYSTEM_PROMPT,
                schemaContext,
                request.getMessage(),
                request.getDatasourceId());

        // 流式调用AgentScope
        String conversationId = DataAgentConstants.INSIGHT_AI_CHAT_CONVERSATION_PREFIX + UUID.randomUUID();
        AgentCallRequest callRequest = new AgentCallRequest();
        callRequest.setMessage(request.getMessage());
        callRequest.setSystemPrompt(systemPrompt);
        callRequest.setSessionId(conversationId);

        Flux<Event> eventFlux = agentScopeService.streamCall(callRequest);
        AtomicReference<String> lastAgentResult = new AtomicReference<>("");

        Disposable disposable = eventFlux.subscribe(
                event -> {
                    if (emitterDone.get()) {
                        return;
                    }
                    String textContent = event.getMessage() != null ? event.getMessage().getTextContent() : null;
                    if (textContent == null || textContent.isEmpty()) {
                        return;
                    }
                    switch (event.getType()) {
                        case REASONING:
                            // 思考过程，推送给用户实时查看
                            sendSseEvent(emitter, "reasoning", textContent);
                            break;
                        case AGENT_RESULT:
                            // 最终结果，只保留最后一轮的内容用于解析Schema
                            lastAgentResult.set(textContent);
                            sendSseEvent(emitter, "content", textContent);
                            break;
                        case TOOL_RESULT:
                            // 工具调用结果，推送给用户了解进度
                            sendSseEvent(emitter, "tool_result", textContent);
                            break;
                        default:
                            break;
                    }
                },
                error -> {
                    log.error("AI生成仪表盘流式调用失败: {}", error.getMessage());
                    if (!emitterDone.compareAndSet(false, true)) {
                        return;
                    }
                    sendSseEvent(emitter, "error", error.getMessage() != null ? error.getMessage() : "AI生成失败");
                    completeEmitterQuietly(emitter, emitterDone);
                },
                () -> {
                    if (!emitterDone.compareAndSet(false, true)) {
                        return;
                    }
                    UserContextHolder.set(userContext);
                    try {
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
                    } catch (Exception e) {
                        log.error("AI生成仪表盘结果解析失败: {}", e.getMessage());
                        sendSseEvent(emitter, "error", "AI生成仪表盘失败：无法解析生成的Schema");
                    } finally {
                        UserContextHolder.clear();
                    }
                    completeEmitterQuietly(emitter, emitterDone);
                }
        );

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

        // 从现有Schema中提取所有datasourceId
        Set<String> datasourceIds = extractDatasourceIds(currentSchema);
        String datasourceContext = buildDatasourceContext(datasourceIds);

        // 构建修改提示词
        String currentSchemaJson;
        try {
            currentSchemaJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(currentSchema);
        } catch (Exception e) {
            throw new BusinessException(500, "当前仪表盘Schema序列化失败");
        }
        // 构建修改系统提示词（格式化后）
        String systemPrompt = String.format(MODIFY_SYSTEM_PROMPT, currentSchemaJson, datasourceContext, request.getMessage());

        // 流式调用AgentScope
        String conversationId = DataAgentConstants.INSIGHT_AI_CHAT_CONVERSATION_PREFIX + UUID.randomUUID();
        AgentCallRequest callRequest = new AgentCallRequest();
        callRequest.setMessage(request.getMessage());
        callRequest.setSystemPrompt(systemPrompt);
        callRequest.setSessionId(conversationId);

        Flux<Event> eventFlux = agentScopeService.streamCall(callRequest);
        AtomicReference<String> lastAgentResult = new AtomicReference<>("");

        Disposable disposable = eventFlux.subscribe(
                event -> {
                    if (emitterDone.get()) {
                        return;
                    }
                    String textContent = event.getMessage() != null ? event.getMessage().getTextContent() : null;
                    if (textContent == null || textContent.isEmpty()) {
                        return;
                    }
                    switch (event.getType()) {
                        case REASONING:
                            sendSseEvent(emitter, "reasoning", textContent);
                            break;
                        case AGENT_RESULT:
                            // 只保留最后一轮的内容用于解析Schema
                            lastAgentResult.set(textContent);
                            sendSseEvent(emitter, "content", textContent);
                            break;
                        case TOOL_RESULT:
                            sendSseEvent(emitter, "tool_result", textContent);
                            break;
                        default:
                            break;
                    }
                },
                error -> {
                    log.error("AI修改仪表盘流式调用失败: {}", error.getMessage());
                    if (!emitterDone.compareAndSet(false, true)) {
                        return;
                    }
                    sendSseEvent(emitter, "error", error.getMessage() != null ? error.getMessage() : "AI修改失败");
                    completeEmitterQuietly(emitter, emitterDone);
                },
                () -> {
                    if (!emitterDone.compareAndSet(false, true)) {
                        return;
                    }
                    UserContextHolder.set(userContext);
                    try {
                        // 流式完成，解析最后一轮Agent结果并更新仪表盘
                        String llmResponse = lastAgentResult.get();
                        log.info("AI修改仪表盘：LLM最终响应长度={}, 前200字符={}", llmResponse.length(),
                                llmResponse.length() > 200 ? llmResponse.substring(0, 200) : llmResponse);
                        String schemaJson = parseLlmResponse(llmResponse);

                        InsightDashboardUpdateRequest updateRequest = new InsightDashboardUpdateRequest();
                        updateRequest.setSchemaJson(schemaJson);
                        InsightDashboardVO result = updateDashboard(dashboardId, updateRequest);

                        sendSseEvent(emitter, "result", objectMapper.writeValueAsString(result));
                    } catch (Exception e) {
                        log.error("AI修改仪表盘结果解析失败: {}", e.getMessage());
                        sendSseEvent(emitter, "error", "AI修改仪表盘失败：无法解析生成的Schema");
                    } finally {
                        UserContextHolder.clear();
                    }
                    completeEmitterQuietly(emitter, emitterDone);
                }
        );

        emitter.onCompletion(disposable::dispose);
        emitter.onTimeout(disposable::dispose);
        emitter.onError(e -> disposable.dispose());
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
     * 构建数据源上下文信息
     */
    private String buildDatasourceContext(Set<String> datasourceIds) {
        if (datasourceIds == null || datasourceIds.isEmpty()) {
            return "当前仪表盘未绑定数据源";
        }
        StringBuilder context = new StringBuilder();
        for (String dsId : datasourceIds) {
            try {
                Long id = Long.parseLong(dsId);
                DatasourceVO datasource = datasourceManageService.getDatasource(id);
                if (datasource != null) {
                    context.append("- 数据源ID: ").append(dsId)
                            .append(", 名称: ").append(datasource.getName())
                            .append(", 类型: ").append(datasource.getSourceType())
                            .append("\n");
                } else {
                    context.append("- 数据源ID: ").append(dsId).append("（未找到）\n");
                }
            } catch (NumberFormatException e) {
                context.append("- 数据源ID: ").append(dsId).append("（格式异常）\n");
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
}
