package vip.mate.dataagent.tool;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.DatasetDataVO;
import vip.mate.dataagent.dto.DatasetFieldVO;
import vip.mate.dataagent.dto.DatasetVO;
import vip.mate.dataagent.service.DatasetManageService;
import vip.mate.datasource.service.EChartsOptionBuilder;
import vip.mate.sdk.service.MateClawRuntime;
import vip.mate.skill.knowledge.SkillScopedToolCallback;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据集查询工具
 * <p>
 * 以 Plugin Tool 方式注册到 MateClaw ToolRegistry，
 * 供"智能问数"等 Agent 通过数据集进行数据查询。
 * <p>
 * 暴露三个动作：
 * <ul>
 *   <li>list_datasets — 列出所有可用数据集</li>
 *   <li>get_dataset_schema — 查看数据集的字段结构</li>
 *   <li>query_dataset_data — 查询数据集的数据（分页）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatasetQueryTool {

    private final DatasetManageService datasetManageService;
    private final MateClawRuntime mateClawRuntime;

    private static final String TOOL_NAME = "query_dataset";

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "action": {
                  "type": "string",
                  "description": "动作：list_datasets / get_dataset_schema / query_dataset_data",
                  "enum": ["list_datasets", "get_dataset_schema", "query_dataset_data"]
                },
                "datasetId": {
                  "type": "integer",
                  "description": "数据集 ID（get_dataset_schema 和 query_dataset_data 时必填）"
                },
                "page": {
                  "type": "integer",
                  "description": "页码，从 1 开始（query_dataset_data 时可选，默认 1）"
                },
                "size": {
                  "type": "integer",
                  "description": "每页条数（query_dataset_data 时可选，默认 50）"
                }
              },
              "required": ["action"]
            }
            """;

    private static final String TOOL_DESCRIPTION = """
            查询数据集的元数据与数据。支持三种动作：
            1. action='list_datasets' — 列出所有可用数据集（无需其他参数）
            2. action='get_dataset_schema' — 查看指定数据集的字段结构与分类（需要 datasetId）
            3. action='query_dataset_data' — 查询指定数据集的数据（需要 datasetId，可选 page 和 size）
            数据集是经过治理的结构化数据视图，字段已区分为维度(dimension)和度量(measure)。
            如果查询结果适合可视化，会自动附带 echarts 图表配置。
            """;

    private static final String ACTION_LIST_DATASETS = "list_datasets";
    private static final String ACTION_GET_DATASET_SCHEMA = "get_dataset_schema";
    private static final String ACTION_QUERY_DATASET_DATA = "query_dataset_data";

    private static final int DEFAULT_PAGE = 1;
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
        log.info("DatasetQueryTool registered as plugin tool: {}", TOOL_NAME);
    }

    private String handleToolCall(String toolInput) {
        try {
            JSONObject input = JSONUtil.parseObj(toolInput);
            String action = input.getStr("action", "");
            return switch (action) {
                case ACTION_LIST_DATASETS -> listDatasets();
                case ACTION_GET_DATASET_SCHEMA -> getDatasetSchema(input);
                case ACTION_QUERY_DATASET_DATA -> queryDatasetData(input);
                default -> error("未知动作: " + action + "，支持: list_datasets / get_dataset_schema / query_dataset_data");
            };
        } catch (Exception e) {
            log.error("数据集查询失败: {}", e.getMessage(), e);
            return error(e.getMessage());
        }
    }

    private String listDatasets() {
        List<DatasetVO> datasets = datasetManageService.listDatasets();
        JSONArray arr = new JSONArray();
        for (DatasetVO ds : datasets) {
            JSONObject obj = new JSONObject(new LinkedHashMap<>());
            obj.set("id", ds.getId());
            obj.set("name", ds.getName());
            obj.set("description", ds.getDescription());
            obj.set("status", ds.getStatus());
            obj.set("rowCount", ds.getRowCount());
            obj.set("columnCount", ds.getColumnCount());
            obj.set("datasourceName", ds.getDatasourceName());
            obj.set("tableNames", ds.getTableNames());
            arr.add(obj);
        }
        JSONObject result = new JSONObject(new LinkedHashMap<>());
        result.set("datasets", arr);
        result.set("count", arr.size());
        return result.toStringPretty();
    }

    private String getDatasetSchema(JSONObject input) {
        Long datasetId = input.getLong("datasetId");
        if (datasetId == null) {
            return error("get_dataset_schema 需要 datasetId 参数");
        }
        DatasetVO dataset = datasetManageService.getDataset(datasetId);
        if (dataset == null) {
            return error("数据集不存在, id=" + datasetId);
        }
        List<DatasetFieldVO> fields = dataset.getFields();
        if (fields == null) {
            fields = datasetManageService.listFields(datasetId);
        }

        JSONObject result = new JSONObject(new LinkedHashMap<>());
        result.set("id", dataset.getId());
        result.set("name", dataset.getName());
        result.set("description", dataset.getDescription());
        result.set("status", dataset.getStatus());
        result.set("rowCount", dataset.getRowCount());
        result.set("columnCount", dataset.getColumnCount());

        JSONArray fieldsArr = new JSONArray();
        for (DatasetFieldVO field : fields) {
            JSONObject f = new JSONObject(new LinkedHashMap<>());
            f.set("name", field.getColumnName());
            f.set("alias", field.getColumnAlias());
            f.set("comment", field.getColumnComment());
            f.set("dataType", field.getDataType());
            f.set("fieldCategory", field.getFieldCategory());
            f.set("primaryKey", field.getPrimaryKey());
            fieldsArr.add(f);
        }
        result.set("fields", fieldsArr);
        return result.toStringPretty();
    }

    private String queryDatasetData(JSONObject input) {
        Long datasetId = input.getLong("datasetId");
        if (datasetId == null) {
            return error("query_dataset_data 需要 datasetId 参数");
        }
        int page = input.getInt("page", DEFAULT_PAGE);
        int size = input.getInt("size", DataAgentConstants.DATASET_DEFAULT_PAGE_SIZE);

        DatasetDataVO data = datasetManageService.getDatasetData(datasetId, page, size);
        if (data == null) {
            return error("数据集数据查询失败, id=" + datasetId);
        }

        List<DatasetDataVO.DatasetColumnDef> columnDefs = data.getColumns();
        List<Map<String, Object>> rows = data.getRows();

        List<String> columns = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        for (DatasetDataVO.DatasetColumnDef colDef : columnDefs) {
            columns.add(colDef.getName());
            titles.add(colDef.getTitle() != null ? colDef.getTitle() : colDef.getName());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**数据集 ID**: ").append(datasetId).append("\n");
        sb.append("**结果**: ").append(rows != null ? rows.size() : 0).append(" 行, ");
        sb.append(columns.size()).append(" 列");
        sb.append(" (第 ").append(page).append(" 页, 每页 ").append(size).append(" 条)");
        sb.append(", 总计 ").append(data.getTotal() != null ? data.getTotal() : 0).append(" 行\n\n");

        if (rows == null || rows.isEmpty()) {
            sb.append("查询结果为空。");
            return sb.toString();
        }

        if (rows.size() <= MARKDOWN_TABLE_THRESHOLD && columns.size() <= MAX_COLUMNS_FOR_TABLE) {
            sb.append("| ").append(String.join(" | ", titles)).append(" |\n");
            sb.append("| ").append("--- | ".repeat(columns.size())).append("\n");
            for (Map<String, Object> row : rows) {
                sb.append("| ");
                for (int i = 0; i < columns.size(); i++) {
                    Object val = row.get(columns.get(i));
                    String valStr = val != null ? String.valueOf(val).replace("|", "\\|") : "NULL";
                    sb.append(valStr);
                    if (i < columns.size() - 1) {
                        sb.append(" | ");
                    }
                }
                sb.append(" |\n");
            }
        } else {
            JSONArray jsonRows = new JSONArray();
            for (Map<String, Object> row : rows) {
                JSONObject obj = new JSONObject(new LinkedHashMap<>());
                for (String col : columns) {
                    obj.set(col, row.get(col));
                }
                jsonRows.add(obj);
            }
            sb.append(jsonRows.toStringPretty());
        }

        List<List<String>> stringRows = toStringRows(columns, rows);
        String chartOption = EChartsOptionBuilder.tryBuild(columns, stringRows);
        if (chartOption != null) {
            sb.append("\n\n```echarts\n").append(chartOption).append("\n```");
        }

        return sb.toString();
    }

    private List<List<String>> toStringRows(List<String> columns, List<Map<String, Object>> rows) {
        List<List<String>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            List<String> stringRow = new ArrayList<>();
            for (String col : columns) {
                Object val = row.get(col);
                stringRow.add(val != null ? String.valueOf(val) : "NULL");
            }
            result.add(stringRow);
        }
        return result;
    }

    private String error(String message) {
        return JSONUtil.toJsonStr(new JSONObject().set("error", message));
    }
}
