package vip.mate.dataagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mate.dataagent.agentscope.dto.AgentCallRequest;
import vip.mate.dataagent.agentscope.dto.AgentCallResponse;
import vip.mate.dataagent.agentscope.service.AgentScopeService;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.*;
import vip.mate.dataagent.exception.BusinessException;
import vip.mate.dataagent.model.InsightDashboardEntity;
import vip.mate.dataagent.repository.InsightDashboardMapper;
import vip.mate.dataagent.service.DatasourceManageService;
import vip.mate.dataagent.service.InsightDashboardService;
import vip.mate.dataagent.service.SchemaEmbeddingService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 洞察仪表盘服务实现
 * <p>
 * 按工作区隔离仪表盘资源，CRUD 操作均校验归属权限。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightDashboardServiceImpl implements InsightDashboardService {

    private final InsightDashboardMapper insightDashboardMapper;
    private final WorkspaceGuard workspaceGuard;
    private final DatasourceManageService datasourceManageService;
    private final SchemaEmbeddingService schemaEmbeddingService;
    private final ObjectMapper objectMapper;
    private final AgentScopeService agentScopeService;

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

    @Override
    public List<InsightDashboardVO> listDashboards() {
        LambdaQueryWrapper<InsightDashboardEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InsightDashboardEntity::getDeleted, 0);
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
        InsightDashboardEntity entity = insightDashboardMapper.selectById(id);
        entity.setDeleted(1);
        insightDashboardMapper.updateById(entity);
    }

    @Override
    public InsightDashboardVO aiChatDashboard(InsightDashboardAiChatRequest request) {
        if (request.getDashboardId() != null && !request.getDashboardId().isBlank()) {
            // 修改模式：构造 ModifyRequest 委托给 modifyDashboard
            InsightDashboardModifyRequest modifyRequest = new InsightDashboardModifyRequest();
            modifyRequest.setDashboardId(Long.valueOf(request.getDashboardId().trim()));
            modifyRequest.setInstruction(request.getMessage());
            return modifyDashboard(modifyRequest);
        }
        // 生成模式：构造 GenerateRequest 委托给 generateDashboard
        InsightDashboardGenerateRequest generateRequest = new InsightDashboardGenerateRequest();
        generateRequest.setName(request.getName());
        generateRequest.setDatasourceId(request.getDatasourceId());
        generateRequest.setDescription(request.getMessage());
        return generateDashboard(generateRequest);
    }

    @Override
    public InsightDashboardVO generateDashboard(InsightDashboardGenerateRequest request) {
        // 1. 参数校验
        validateGenerateRequest(request);

        // 2. 查询数据源信息
        DatasourceVO datasource = datasourceManageService.getDatasource(request.getDatasourceId());
        if (datasource == null) {
            throw new BusinessException(404, "数据源不存在: " + request.getDatasourceId());
        }

        // 3. 获取数据源的Schema信息（表结构、语义模型）
        String schemaContext = buildSchemaContext(request.getDatasourceId(), request.getDescription());

        // 4. 构建提示词
        String prompt = String.format(GENERATE_SYSTEM_PROMPT,
                schemaContext,
                request.getDescription(),
                request.getDatasourceId());

        // 5. 调用AgentScope生成Schema
        String conversationId = DataAgentConstants.INSIGHT_AI_CHAT_CONVERSATION_PREFIX + UUID.randomUUID();
        AgentCallRequest callRequest = new AgentCallRequest();
        callRequest.setMessage(prompt);
        callRequest.setSystemPrompt(GENERATE_SYSTEM_PROMPT);
        callRequest.setSessionId(conversationId);
        AgentCallResponse callResponse = agentScopeService.call(callRequest);
        if (!callResponse.isSuccess()) {
            throw new BusinessException(500, "AI生成仪表盘失败: " + callResponse.getErrorMessage());
        }
        String llmResponse = callResponse.getContent();

        // 6. 解析LLM返回的JSON
        String schemaJson = parseLlmResponse(llmResponse);

        // 7. 创建仪表盘
        InsightDashboardCreateRequest createRequest = new InsightDashboardCreateRequest();
        createRequest.setName(request.getName());
        createRequest.setDescription(request.getDescription());
        createRequest.setSchemaJson(schemaJson);
        return createDashboard(createRequest);
    }

    @Override
    public InsightDashboardVO modifyDashboard(InsightDashboardModifyRequest request) {
        // 1. 参数校验
        validateModifyRequest(request);

        // 2. 获取现有仪表盘
        requireOwnership(request.getDashboardId());
        InsightDashboardEntity entity = insightDashboardMapper.selectById(request.getDashboardId());

        // 3. 解析现有Schema
        InsightDashboardSchemaDTO currentSchema;
        try {
            currentSchema = objectMapper.readValue(entity.getSchemaJson(), InsightDashboardSchemaDTO.class);
        } catch (Exception e) {
            throw new BusinessException(500, "当前仪表盘Schema解析失败，无法进行AI修改");
        }

        // 4. 从现有Schema中提取所有datasourceId，查询数据源信息
        Set<String> datasourceIds = extractDatasourceIds(currentSchema);
        String datasourceContext = buildDatasourceContext(datasourceIds);

        // 5. 构建修改提示词
        String currentSchemaJson;
        try {
            currentSchemaJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(currentSchema);
        } catch (Exception e) {
            throw new BusinessException(500, "当前仪表盘Schema序列化失败");
        }
        String prompt = String.format(MODIFY_SYSTEM_PROMPT, currentSchemaJson, datasourceContext, request.getInstruction());

        // 6. 调用AgentScope生成修改后的Schema
        String conversationId = DataAgentConstants.INSIGHT_AI_CHAT_CONVERSATION_PREFIX + UUID.randomUUID();
        AgentCallRequest callRequest = new AgentCallRequest();
        callRequest.setMessage(prompt);
        callRequest.setSystemPrompt(MODIFY_SYSTEM_PROMPT);
        callRequest.setSessionId(conversationId);
        AgentCallResponse callResponse = agentScopeService.call(callRequest);
        if (!callResponse.isSuccess()) {
            throw new BusinessException(500, "AI修改仪表盘失败: " + callResponse.getErrorMessage());
        }
        String llmResponse = callResponse.getContent();

        // 7. 解析LLM返回的JSON（复用已有的解析方法）
        String schemaJson = parseLlmResponse(llmResponse);

        // 8. 更新仪表盘的schemaJson字段
        InsightDashboardUpdateRequest updateRequest = new InsightDashboardUpdateRequest();
        updateRequest.setSchemaJson(schemaJson);
        return updateDashboard(request.getDashboardId(), updateRequest);
    }

    private void validateGenerateRequest(InsightDashboardGenerateRequest request) {
        if (request.getDatasourceId() == null) {
            throw new BusinessException(400, "数据源ID不能为空");
        }
        if (request.getDescription() == null || request.getDescription().isBlank()) {
            throw new BusinessException(400, "需求描述不能为空");
        }
    }

    /**
     * 校验AI修改仪表盘请求参数
     */
    private void validateModifyRequest(InsightDashboardModifyRequest request) {
        if (request.getDashboardId() == null) {
            throw new BusinessException(400, "仪表盘ID不能为空");
        }
        if (request.getInstruction() == null || request.getInstruction().isBlank()) {
            throw new BusinessException(400, "修改指令不能为空");
        }
    }

    /**
     * 从仪表盘Schema中提取所有数据源ID
     * <p>
     * 遍历所有pages的components，收集dataSource.datasourceId。
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
     * <p>
     * 根据数据源ID列表查询数据源名称，拼接为可读的上下文描述。
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
     * <p>
     * 优先使用语义检索获取与用户需求相关的表，再补充表字段详情。
     * 如果语义检索无结果，则回退到列出所有表。
     */
    private String buildSchemaContext(Long datasourceId, String userDescription) {
        StringBuilder context = new StringBuilder();

        // 通过语义检索获取相关表
        SchemaSearchRequest searchRequest = new SchemaSearchRequest();
        searchRequest.setDatasourceId(datasourceId);
        searchRequest.setQuery(userDescription);
        searchRequest.setTopK(DataAgentConstants.INSIGHT_GENERATE_SCHEMA_TOP_K);
        searchRequest.setSimilarityThreshold(DataAgentConstants.SCHEMA_SEARCH_DEFAULT_THRESHOLD);

        SchemaSearchResult searchResult = schemaEmbeddingService.searchSchema(searchRequest);

        if (searchResult.getTableHits() != null && !searchResult.getTableHits().isEmpty()) {
            // 语义检索有结果，使用相关表
            context.append("相关数据表（基于语义检索）：\n");
            for (SchemaSearchResult.TableHit hit : searchResult.getTableHits()) {
                appendTableInfo(context, datasourceId, hit.getTableName(), hit.getTableComment(), hit.getSemanticFields());
            }
        } else {
            // 语义检索无结果，回退到列出所有表
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

        // 补充表间关联关系
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

        // 优先使用语义模型字段信息
        if (semanticFields != null && !semanticFields.isEmpty()) {
            context.append("  字段: ");
            for (SemanticModelVO field : semanticFields) {
                context.append(field.getPromptInfo()).append("; ");
            }
            context.append("\n");
        } else {
            // 回退到查询表字段详情
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
     * <p>
     * 清洗LLM输出（去除markdown代码块包裹），并验证JSON格式。
     */
    private String parseLlmResponse(String llmResponse) {
        if (llmResponse == null || llmResponse.isBlank()) {
            throw new BusinessException(500, "AI生成失败：LLM返回为空");
        }

        String content = llmResponse.trim();

        // 去除markdown代码块包裹
        content = stripMarkdownCodeBlock(content);

        // 验证JSON格式
        try {
            InsightDashboardSchemaDTO schema = objectMapper.readValue(content, InsightDashboardSchemaDTO.class);
            // 补全组件布局信息
            patchComponentLayout(schema);
            // 重新序列化为JSON字符串
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
        // 处理 ```json ... ``` 包裹
        if (content.contains("```json")) {
            int start = content.indexOf("```json");
            int end = content.lastIndexOf("```");
            if (start >= 0 && end > start + 6) {
                content = content.substring(start + 7, end).trim();
            }
        } else if (content.contains("```")) {
            // 处理 ``` ... ``` 包裹
            int start = content.indexOf("```");
            int end = content.lastIndexOf("```");
            if (start >= 0 && end > start + 2) {
                content = content.substring(start + 3, end).trim();
                // 去除可能的语言标识行
                if (content.contains("\n")) {
                    String firstLine = content.substring(0, content.indexOf("\n")).trim();
                    if (firstLine.matches("[a-zA-Z]+")) {
                        content = content.substring(content.indexOf("\n") + 1).trim();
                    }
                }
            }
        }

        // 尝试提取第一个 { 到最后一个 } 之间的内容
        int firstBrace = content.indexOf('{');
        int lastBrace = content.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            content = content.substring(firstBrace, lastBrace + 1);
        }

        return content;
    }

    /**
     * 补全组件布局信息
     * <p>
     * 对LLM生成的组件进行布局修正：确保position合理、组件ID存在、
     * renderType与type一致、datasourceId已填充。
     */
    private void patchComponentLayout(InsightDashboardSchemaDTO schema) {
        if (schema.getPages() == null || schema.getPages().isEmpty()) {
            return;
        }

        int componentIndex = 0;
        for (InsightDashboardSchemaDTO.Page page : schema.getPages()) {
            // 补全页面ID
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
                // 补全组件ID
                if (component.getId() == null || component.getId().isBlank()) {
                    component.setId("comp_" + componentIndex);
                }
                componentIndex++;

                // 补全position
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

                // 补全renderType
                if (component.getRenderType() == null || component.getRenderType().isBlank()) {
                    String renderType = resolveRenderType(component.getType());
                    if (renderType != null) {
                        component.setRenderType(renderType);
                    }
                }

                // 如果type是chart但chartType为空，默认设为bar
                if ("chart".equals(component.getType()) && (component.getChartType() == null || component.getChartType().isBlank())) {
                    component.setChartType(DataAgentConstants.CHART_TYPE_BAR);
                }

                // 补全dataSource中的datasourceId（从pages级别继承的场景已在prompt中指定）
                if (component.getDataSource() != null && (component.getDataSource().getDatasourceId() == null || component.getDataSource().getDatasourceId().isBlank())) {
                    // datasourceId已在prompt中要求LLM填充，此处为兜底
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
