package vip.mate.dataagent.tool;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.LogicalRelationVO;
import vip.mate.dataagent.dto.SchemaSearchRequest;
import vip.mate.dataagent.dto.SchemaSearchResult;
import vip.mate.dataagent.dto.SemanticModelVO;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.repository.DatasourceMapper;
import vip.mate.dataagent.service.DatasourceManageService;
import vip.mate.dataagent.service.LogicalRelationService;
import vip.mate.dataagent.service.SchemaEmbeddingService;
import vip.mate.dataagent.service.SemanticModelService;
import vip.mate.dataagent.util.JdbcUtils;
import vip.mate.datasource.service.EChartsOptionBuilder;
import vip.mate.datasource.service.SqlValidationService;
import vip.mate.sdk.service.MateClawRuntime;
import vip.mate.skill.knowledge.SkillScopedToolCallback;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

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
    private final SqlValidationService sqlValidationService;
    private final MateClawRuntime mateClawRuntime;
    private final SchemaEmbeddingService schemaEmbeddingService;
    private final SemanticModelService semanticModelService;
    private final LogicalRelationService logicalRelationService;

    private static final String TOOL_NAME = "query_datasource";

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
            对于复杂聚合、多表关联、精确数值计算等场景，优先使用 execute_sql 而非分页获取数据后在内存中计算。
            """;

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "action": {
                  "type": "string",
                  "description": "动作：list_datasources / list_tables / execute_sql / search_schema",
                  "enum": ["list_datasources", "list_tables", "execute_sql", "search_schema"]
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
                  "description": "自然语言查询，用于语义检索相关表（search_schema 时必填）"
                }
              },
              "required": ["action"]
            }
            """;

    private static final String ACTION_LIST_DATASOURCES = "list_datasources";
    private static final String ACTION_LIST_TABLES = "list_tables";
    private static final String ACTION_EXECUTE_SQL = "execute_sql";
    private static final String ACTION_SEARCH_SCHEMA = "search_schema";

    private static final int QUERY_TIMEOUT_SECONDS = 30;
    private static final int MAX_ROWS = 500;
    private static final int MARKDOWN_TABLE_THRESHOLD = 20;
    private static final int MAX_COLUMNS_FOR_TABLE = 10;

    @PostConstruct
    public void register() {
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
                default -> error("未知动作: " + action + "，支持: list_datasources / list_tables / execute_sql / search_schema");
            };
        } catch (Exception e) {
            log.error("数据源查询失败: {}", e.getMessage(), e);
            return error(e.getMessage());
        }
    }

    /**
     * 列出所有可用数据源
     */
    private String listDatasources() {
        var datasources = datasourceManageService.listDatasources();
        JSONArray arr = new JSONArray();
        for (var ds : datasources) {
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
        return result.toStringPretty();
    }

    /**
     * 列出指定数据源下的所有表
     */
    private String listTables(JSONObject input) {
        Long datasourceId = input.getLong("datasourceId");
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
     * 执行只读 SQL 查询
     */
    private String executeSql(JSONObject input) {
        Long datasourceId = input.getLong("datasourceId");
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

        String jdbcUrl = JdbcUtils.buildJdbcUrl(entity);

        // 3. 执行查询（只读模式）
        long startTime = System.currentTimeMillis();
        try (Connection conn = DriverManager.getConnection(jdbcUrl, entity.getUsername(), entity.getPassword())) {
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
     * 语义检索相关表
     */
    private String searchSchema(JSONObject input) {
        Long datasourceId = input.getLong("datasourceId");
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

    private String error(String message) {
        return JSONUtil.toJsonStr(new JSONObject().set("error", message));
    }
}
