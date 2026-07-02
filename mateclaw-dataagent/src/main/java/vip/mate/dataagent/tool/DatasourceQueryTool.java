package vip.mate.dataagent.tool;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vip.mate.agent.context.ChatOrigin;
import vip.mate.agent.context.ChatOriginHolder;
import vip.mate.dataagent.auth.context.UserContextHolder;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.BusinessTermSearchResult;
import vip.mate.dataagent.dto.LogicalRelationVO;
import vip.mate.dataagent.dto.SchemaSearchRequest;
import vip.mate.dataagent.dto.SchemaSearchResult;
import vip.mate.dataagent.dto.SemanticModelVO;
import vip.mate.dataagent.model.DatasourceAccountEntity;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.repository.DatasourceMapper;
import vip.mate.dataagent.service.BusinessTermEsService;
import vip.mate.dataagent.service.DatasourceAccountService;
import vip.mate.dataagent.service.DatasourceManageService;
import vip.mate.dataagent.service.LogicalRelationService;
import vip.mate.dataagent.service.SchemaEmbeddingService;
import vip.mate.dataagent.service.SemanticModelService;
import vip.mate.dataagent.support.DataAgentChatScopeContext;
import vip.mate.dataagent.support.DataAgentChatScopeContext.ScopeResolveResult;
import vip.mate.dataagent.util.JdbcUtils;
import vip.mate.datasource.service.EChartsOptionBuilder;
import vip.mate.datasource.service.SqlValidationService;
import vip.mate.sdk.service.MateClawRuntime;
import vip.mate.skill.knowledge.SkillScopedToolCallback;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 数据源只读 SQL 查询工具
 * <p>
 * 以 Plugin Tool 方式注册到 MateClaw ToolRegistry，
 * 供 Agent 直接在数据源上执行只读 SQL 查询，避免内存计算出错。
 * <p>
 * 暴露四个动作：
 * <ul>
 *   <li>list_datasources — 列出所有可用的数据源</li>
 *   <li>list_tables — 列出指定数据源下的所有表</li>
 *   <li>execute_sql — 执行只读 SQL 查询（仅允许 SELECT）</li>
 *   <li>search_schema — 语义检索相关表，返回 Top-K 相关表的语义描述和关联关系</li>
 * </ul>
 * <p>
 * 安全保障：
 * <ul>
 *   <li>仅允许 SELECT 语句，禁止 INSERT/UPDATE/DELETE/DROP 等写操作</li>
 *   <li>无 LIMIT 时自动注入 LIMIT 500</li>
 *   <li>查询超时 30 秒</li>
 *   <li>最大返回 500 行</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatasourceQueryTool {

    private final DatasourceManageService datasourceManageService;
    private final DatasourceMapper datasourceMapper;
    private final DatasourceAccountService datasourceAccountService;
    private final SqlValidationService sqlValidationService;
    private final MateClawRuntime mateClawRuntime;
    private final SchemaEmbeddingService schemaEmbeddingService;
    private final SemanticModelService semanticModelService;
    private final LogicalRelationService logicalRelationService;
    private final BusinessTermEsService businessTermEsService;
    private final DataAgentChatScopeContext scopeContext;

    private static final String TOOL_NAME = "data_query";

    private static final String TOOL_DESCRIPTION = """
            在数据源上执行只读 SQL 查询，用于复杂分析和精确计算。
            仅允许 SELECT 语句，禁止 INSERT/UPDATE/DELETE/DROP 等写操作。
            如果 SQL 没有 LIMIT 子句会自动添加 LIMIT 500。
            返回查询结果（Markdown 表格或 JSON 格式）以及行数和执行耗时。
            如果数据适合可视化，会自动附带 echarts 图表配置。
            支持四种动作：
            1. action='list_datasources' — 列出所有可用数据源（无需其他参数）
            2. action='list_tables' — 列出指定数据源下的所有表（需要 datasourceId）
            3. action='execute_sql' — 执行只读 SQL 查询（需要 datasourceId 和 sql）
            4. action='search_schema' — 语义检索相关表（需要 datasourceId 和 query），返回 Top-K 相关表的语义描述和关联关系，用于理解数据结构后再编写 SQL
            5. action='search_business_term' — 搜索业务术语和同义词（需要 query），帮助理解用户查询中的业务术语含义
            对于复杂聚合、多表关联、精确数值计算等场景，优先使用 execute_sql 而非分页获取数据后在内存中计算。
            """;

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "action": {
                  "type": "string",
                  "description": "动作：list_datasources / list_tables / execute_sql / search_schema / search_business_term",
                  "enum": ["list_datasources", "list_tables", "execute_sql", "search_schema", "search_business_term"]
                },
                "datasourceId": {
                  "type": "integer",
                  "description": "数据源 ID（list_tables、execute_sql 和 search_schema 时必填）"
                },
                "sql": {
                  "type": "string",
                  "description": "要执行的 SQL 查询，仅允许 SELECT 语句（execute_sql 时必填）"
                },
                "query": {
                  "type": "string",
                  "description": "自然语言查询，用于语义检索相关表（search_schema 时必填）或搜索业务术语（search_business_term 时必填）"
                }
              },
              "required": ["action"]
            }
            """;

    private static final String ACTION_LIST_DATASOURCES = "list_datasources";
    private static final String ACTION_LIST_TABLES = "list_tables";
    private static final String ACTION_EXECUTE_SQL = "execute_sql";
    private static final String ACTION_SEARCH_SCHEMA = "search_schema";
    private static final String ACTION_SEARCH_BUSINESS_TERM = "search_business_term";

    private static final int QUERY_TIMEOUT_SECONDS = 30;
    private static final int MAX_ROWS = 500;
    private static final int MARKDOWN_TABLE_THRESHOLD = 20;
    private static final int MAX_COLUMNS_FOR_TABLE = 10;

    /**
     * mateclaw-server 中默认提供的内置数据源工具 Bean 名称。
     * <p>
     * dataagent 自身已实现 data_query 工具覆盖数据源元数据查询场景，
     * 为避免 Agent 因 server 内置工具而绕过 dataagent 的数据源（dataagent_datasource）查询逻辑，
     * 启动时禁用该 Bean。
     */
    private static final String SERVER_DATASOURCE_TOOL_BEAN = "datasourceTool";

    @PostConstruct
    public void register() {
        // 禁用 mateclaw-server 内置 DatasourceTool，避免与本工具的数据源查询动作冲突
        try {
            mateClawRuntime.disableBuiltinToolByBeanName(SERVER_DATASOURCE_TOOL_BEAN);
        } catch (Exception e) {
            log.warn("Failed to disable builtin tool {}: {}", SERVER_DATASOURCE_TOOL_BEAN, e.getMessage());
        }

        SkillScopedToolCallback toolCallback = new SkillScopedToolCallback(
                TOOL_NAME,
                TOOL_DESCRIPTION,
                INPUT_SCHEMA,
                this::handleToolCall
        );
        mateClawRuntime.registerTool(toolCallback);
        log.info("DatasourceQueryTool registered as plugin tool: {}", TOOL_NAME);
    }

    private String handleToolCall(String toolInput) {
        try {
            JSONObject input = JSONUtil.parseObj(toolInput);
            String action = input.getStr("action", "");
            return switch (action) {
                case ACTION_LIST_DATASOURCES -> listDatasources();
                case ACTION_LIST_TABLES -> listTables(input);
                case ACTION_EXECUTE_SQL -> executeSql(input);
                case ACTION_SEARCH_SCHEMA -> searchSchema(input);
                case ACTION_SEARCH_BUSINESS_TERM -> searchBusinessTerm(input);
                default -> error("未知动作: " + action + "，支持: list_datasources / list_tables / execute_sql / search_schema / search_business_term");
            };
        } catch (Exception e) {
            log.error("数据源查询失败: {}", e.getMessage(), e);
            return error(e.getMessage());
        }
    }

    /**
     * 列出所有可用数据源（受用户勾选白名单约束）
     */
    private String listDatasources() {
        Set<Long> allowed = currentDatasourceWhitelist();
        var datasources = datasourceManageService.listDatasources();
        JSONArray arr = new JSONArray();
        for (var ds : datasources) {
            if (Boolean.FALSE.equals(ds.getEnabled())) {
                continue;
            }
            if (!allowed.isEmpty() && (ds.getId() == null || !allowed.contains(ds.getId()))) {
                continue;
            }
            JSONObject obj = new JSONObject();
            obj.set("id", ds.getId());
            obj.set("name", ds.getName());
            obj.set("sourceType", ds.getSourceType());
            obj.set("description", ds.getDescription());
            obj.set("enabled", ds.getEnabled());
            obj.set("tableCount", ds.getTableCount());
            arr.add(obj);
        }
        JSONObject result = new JSONObject();
        result.set("datasources", arr);
        result.set("count", arr.size());
        if (!allowed.isEmpty()) {
            result.set("scopeNotice", "用户已限定数据源范围，仅返回白名单内的数据源");
        }
        return result.toStringPretty();
    }

    /**
     * 列出指定数据源下的所有表（仅当 datasourceId 在白名单内时允许）
     */
    private String listTables(JSONObject input) {
        Long datasourceId = input.getLong("datasourceId");

        // 解析数据源白名单（含单值自动注入、可用列表引导）
        ChatOrigin dsOrigin = ChatOriginHolder.get();
        String dsConvId = dsOrigin != null ? dsOrigin.conversationId() : null;
        ScopeResolveResult<Long> dsScope = scopeContext.resolveDatasourceId(dsConvId, datasourceId);
        if (dsScope.hasError()) {
            return error(dsScope.getErrorMessage());
        }
        datasourceId = dsScope.getResolvedValue();
        if (datasourceId == null) {
            return error("list_tables 需要 datasourceId 参数");
        }
        var tables = datasourceManageService.listTables(datasourceId);
        JSONArray arr = new JSONArray();
        for (var table : tables) {
            JSONObject obj = new JSONObject();
            obj.set("id", table.getId());
            obj.set("tableName", table.getTableName());
            obj.set("tableComment", table.getTableComment());
            obj.set("tableType", table.getTableType());
            obj.set("columnCount", table.getColumnCount());

            // 附带语义模型信息（如果有）
            List<SemanticModelVO> semanticFields = semanticModelService.listByDatasourceIdAndTableNames(
                    datasourceId, List.of(table.getTableName()));
            if (semanticFields != null && !semanticFields.isEmpty()) {
                obj.set("semanticFieldCount", semanticFields.size());
                // 取前3个语义信息作为摘要
                JSONArray semanticArr = new JSONArray();
                semanticFields.stream().limit(3).forEach(f -> semanticArr.add(f.getPromptInfo()));
                obj.set("semanticSummary", semanticArr);
            }

            arr.add(obj);
        }
        JSONObject result = new JSONObject();
        result.set("datasourceId", datasourceId);
        result.set("tables", arr);
        result.set("count", arr.size());
        return result.toStringPretty();
    }

    /**
     * 执行只读 SQL 查询（仅当 datasourceId 在白名单内时允许）
     */
    private String executeSql(JSONObject input) {
        Long datasourceId = input.getLong("datasourceId");

        // 解析数据源白名单（含单值自动注入、可用列表引导）
        ChatOrigin dsOrigin = ChatOriginHolder.get();
        String dsConvId = dsOrigin != null ? dsOrigin.conversationId() : null;
        ScopeResolveResult<Long> dsScope = scopeContext.resolveDatasourceId(dsConvId, datasourceId);
        if (dsScope.hasError()) {
            return error(dsScope.getErrorMessage());
        }
        datasourceId = dsScope.getResolvedValue();
        if (datasourceId == null) {
            return error("execute_sql 需要 datasourceId 参数");
        }
        String sql = input.getStr("sql");
        if (sql == null || sql.isBlank()) {
            return error("execute_sql 需要 sql 参数");
        }

        // 1. 验证并规范化 SQL（仅允许 SELECT，自动注入 LIMIT）
        String safeSql;
        try {
            safeSql = sqlValidationService.validateAndNormalize(sql);
        } catch (IllegalArgumentException e) {
            return error("SQL 验证失败: " + e.getMessage());
        }

        log.info("执行只读 SQL 查询 [数据源 {}]: {}", datasourceId, safeSql);

        // 2. 获取数据源连接信息
        DatasourceEntity entity = datasourceMapper.selectById(datasourceId);
        if (entity == null) {
            return error("数据源不存在, id=" + datasourceId);
        }
        if (entity.getEnabled() == null || !entity.getEnabled()) {
            return error("数据源已禁用, id=" + datasourceId);
        }

        // 3. 解析查询账号：必须使用用户绑定的查询账号，不允许回退到数据源管理员账号
        Long currentUserId = UserContextHolder.getUserId();
        if (currentUserId == null) {
            return error("当前用户未登录，无法执行 SQL 查询");
        }
        DatasourceAccountEntity account = datasourceAccountService.getByDatasourceIdAndUserId(datasourceId, currentUserId);
        if (account == null || account.getStatus() == null || account.getStatus() != 1) {
            return error("当前用户未绑定数据源查询账号，请先在数据源页面配置查询账号后再执行查询");
        }
        String queryUsername = account.getQueryUsername();
        String queryPassword = account.getQueryPassword();
        log.info("用户 {} 使用自定义查询账号连接数据源 {}", currentUserId, datasourceId);

        String jdbcUrl = JdbcUtils.buildJdbcUrl(entity);

        // 4. 执行查询（只读模式）
        long startTime = System.currentTimeMillis();
        try (Connection conn = DriverManager.getConnection(jdbcUrl, queryUsername, queryPassword)) {
            conn.setReadOnly(true);
            try (Statement stmt = conn.createStatement()) {
                stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                stmt.setMaxRows(MAX_ROWS);
                ResultSet rs = stmt.executeQuery(safeSql);
                long elapsed = System.currentTimeMillis() - startTime;
                return formatResult(rs, safeSql, elapsed);
            }
        } catch (SQLException e) {
            log.error("SQL 查询执行失败: {}", e.getMessage(), e);
            return error("SQL 查询执行失败: " + e.getMessage());
        }
    }

    /**
     * 格式化查询结果
     */
    private String formatResult(ResultSet rs, String sql, long elapsedMs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();

        // 收集列名
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= colCount; i++) {
            columns.add(meta.getColumnLabel(i));
        }

        // 收集数据行
        List<List<String>> rows = new ArrayList<>();
        while (rs.next() && rows.size() < MAX_ROWS) {
            List<String> row = new ArrayList<>();
            for (int i = 1; i <= colCount; i++) {
                String val = rs.getString(i);
                row.add(val != null ? val : "NULL");
            }
            rows.add(row);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**SQL**: `").append(sql).append("`\n");
        sb.append("**结果**: ").append(rows.size()).append(" 行, ").append(colCount).append(" 列");
        sb.append(" (耗时 ").append(elapsedMs).append("ms)\n\n");

        if (rows.isEmpty()) {
            sb.append("查询结果为空。");
            return sb.toString();
        }

        if (rows.size() <= MARKDOWN_TABLE_THRESHOLD && colCount <= MAX_COLUMNS_FOR_TABLE) {
            // Markdown 表格格式
            sb.append("| ").append(String.join(" | ", columns)).append(" |\n");
            sb.append("| ").append("--- | ".repeat(colCount)).append("\n");
            for (List<String> row : rows) {
                sb.append("| ");
                for (int i = 0; i < row.size(); i++) {
                    sb.append(row.get(i).replace("|", "\\|"));
                    if (i < row.size() - 1) {
                        sb.append(" | ");
                    }
                }
                sb.append(" |\n");
            }
        } else {
            // JSON 格式（大结果集）
            JSONArray jsonRows = new JSONArray();
            for (List<String> row : rows) {
                JSONObject obj = new JSONObject();
                for (int i = 0; i < columns.size(); i++) {
                    obj.set(columns.get(i), row.get(i));
                }
                jsonRows.add(obj);
            }
            sb.append(jsonRows.toStringPretty());
        }

        if (rows.size() >= MAX_ROWS) {
            sb.append("\n\n> 结果已截断至 ").append(MAX_ROWS).append(" 行，实际数据可能更多。");
        }

        // 自动生成 ECharts 图表配置
        String chartOption = EChartsOptionBuilder.tryBuild(columns, rows);
        if (chartOption != null) {
            sb.append("\n\n```echarts\n").append(chartOption).append("\n```");
        }

        return sb.toString();
    }

    /**
     * 语义检索相关表（仅当 datasourceId 在白名单内时允许）
     */
    private String searchSchema(JSONObject input) {
        Long datasourceId = input.getLong("datasourceId");

        // 解析数据源白名单（含单值自动注入、可用列表引导）
        ChatOrigin dsOrigin = ChatOriginHolder.get();
        String dsConvId = dsOrigin != null ? dsOrigin.conversationId() : null;
        ScopeResolveResult<Long> dsScope = scopeContext.resolveDatasourceId(dsConvId, datasourceId);
        if (dsScope.hasError()) {
            return error(dsScope.getErrorMessage());
        }
        datasourceId = dsScope.getResolvedValue();
        if (datasourceId == null) {
            return error("search_schema 需要 datasourceId 参数");
        }
        String query = input.getStr("query");
        if (query == null || query.isBlank()) {
            return error("search_schema 需要 query 参数");
        }

        SchemaSearchRequest request = new SchemaSearchRequest();
        request.setDatasourceId(datasourceId);
        request.setQuery(query);
        request.setTopK(DataAgentConstants.SCHEMA_SEARCH_DEFAULT_TOP_K);
        request.setSimilarityThreshold(DataAgentConstants.SCHEMA_SEARCH_DEFAULT_THRESHOLD);

        SchemaSearchResult result = schemaEmbeddingService.searchSchema(request);

        StringBuilder sb = new StringBuilder();
        sb.append("**查询**: ").append(query).append("\n");
        sb.append("**数据源 ID**: ").append(datasourceId).append("\n");
        sb.append("**检索耗时**: ").append(result.getElapsedMs()).append("ms\n\n");

        if (result.getTableHits() == null || result.getTableHits().isEmpty()) {
            sb.append("未找到相关表。请尝试使用 list_tables 查看所有可用表。");
            return sb.toString();
        }

        sb.append("## 相关表 (").append(result.getTableHits().size()).append(" 个)\n\n");
        for (SchemaSearchResult.TableHit hit : result.getTableHits()) {
            sb.append("### ").append(hit.getTableName());
            if (hit.getTableComment() != null && !hit.getTableComment().isBlank()) {
                sb.append(" - ").append(hit.getTableComment());
            }
            sb.append(" [匹配分数: ").append(String.format("%.3f", hit.getScore()));
            sb.append(", 来源: ").append(hit.getMatchSource()).append("]\n\n");

            if (hit.getSemanticFields() != null && !hit.getSemanticFields().isEmpty()) {
                sb.append("**字段语义信息**:\n");
                for (SemanticModelVO field : hit.getSemanticFields()) {
                    sb.append("- ").append(field.getPromptInfo()).append("\n");
                }
                sb.append("\n");
            }
        }

        if (result.getRelations() != null && !result.getRelations().isEmpty()) {
            sb.append("## 表间关联关系\n\n");
            for (LogicalRelationVO rel : result.getRelations()) {
                sb.append("- ").append(rel.getPromptInfo()).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 搜索业务术语和同义词（跨所有业务域/租户）
     */
    private String searchBusinessTerm(JSONObject input) {
        String query = input.getStr("query");
        if (query == null || query.isBlank()) {
            return error("search_business_term 需要 query 参数");
        }

        int topK = input.getInt("topK", DataAgentConstants.BUSINESS_TERM_SEARCH_DEFAULT_TOP_K);
        double threshold = input.getDouble("similarityThreshold",
                DataAgentConstants.BUSINESS_TERM_SEARCH_DEFAULT_THRESHOLD);

        BusinessTermSearchResult result = businessTermEsService.hybridSearch(query, topK, threshold);

        StringBuilder sb = new StringBuilder();
        sb.append("**查询**: ").append(query).append("\n");

        int hitCount = result.getTermHits() != null ? result.getTermHits().size() : 0;
        sb.append("**匹配结果**: ").append(hitCount).append(" 个术语");
        sb.append(" (检索耗时: ").append(result.getElapsedMs()).append("ms)\n\n");

        if (result.getTermHits() != null && !result.getTermHits().isEmpty()) {
            sb.append("## 术语匹配\n\n");
            for (BusinessTermSearchResult.TermHit hit : result.getTermHits()) {
                sb.append("- **").append(hit.getTermName()).append("**");
                if (hit.getSynonyms() != null && !hit.getSynonyms().isBlank()) {
                    sb.append("（同义词: ").append(hit.getSynonyms()).append("）");
                }
                if (hit.getDescription() != null && !hit.getDescription().isBlank()) {
                    sb.append(" - ").append(hit.getDescription());
                }
                if (hit.getCategory() != null && !hit.getCategory().isBlank()) {
                    sb.append(" [分类: ").append(hit.getCategory()).append("]");
                }
                sb.append(" [分数: ").append(String.format("%.3f", hit.getScore()));
                sb.append(", 来源: ").append(hit.getMatchSource()).append("]\n");
            }
            sb.append("\n");
        }

        if (hitCount == 0) {
            sb.append("未找到匹配的业务术语。请尝试更换关键词。");
        }

        return sb.toString();
    }

    private String error(String message) {
        return JSONUtil.toJsonStr(new JSONObject().set("error", message));
    }

    /**
     * 读取当前会话的数据源白名单。
     * <p>
     * 通过 {@link ChatOriginHolder} 拿到当前 conversationId，再从
     * {@link DataAgentChatScopeContext} 中取出前端勾选时写入的白名单。
     * 当返回空集合时表示未配置白名单（不做约束）。
     */
    private Set<Long> currentDatasourceWhitelist() {
        ChatOrigin origin = ChatOriginHolder.get();
        if (origin == null || origin.conversationId() == null || origin.conversationId().isBlank()) {
            return Set.of();
        }
        return scopeContext.getAllowedDatasourceIds(origin.conversationId());
    }
}
