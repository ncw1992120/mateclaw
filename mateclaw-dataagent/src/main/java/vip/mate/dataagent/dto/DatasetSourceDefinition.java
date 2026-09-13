package vip.mate.dataagent.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/**
 * 数据集来源的类型化定义。连接凭据仍保存在数据源连接上，定义只保存可复用的数据集引用。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "sourceType", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DatasetSourceDefinition.JdbcTableDefinition.class, name = "JDBC_TABLE"),
        @JsonSubTypes.Type(value = DatasetSourceDefinition.JdbcSqlDefinition.class, name = "JDBC_SQL"),
        @JsonSubTypes.Type(value = DatasetSourceDefinition.AloudataViewDefinition.class, name = "ALOUDATA_ANALYSIS_VIEW"),
        @JsonSubTypes.Type(value = DatasetSourceDefinition.HttpApiDefinition.class, name = "HTTP_API"),
        @JsonSubTypes.Type(value = DatasetSourceDefinition.FileDefinition.class, name = "FILE")
})
public sealed interface DatasetSourceDefinition
        permits DatasetSourceDefinition.JdbcTableDefinition,
        DatasetSourceDefinition.JdbcSqlDefinition,
        DatasetSourceDefinition.AloudataViewDefinition,
        DatasetSourceDefinition.HttpApiDefinition,
        DatasetSourceDefinition.FileDefinition {

    String sourceType();

    record JdbcTableDefinition(Long datasourceId, List<String> tableIds) implements DatasetSourceDefinition {
        public JdbcTableDefinition {
            requireDatasource(datasourceId);
            if (tableIds == null || tableIds.isEmpty() || tableIds.stream().anyMatch(v -> v == null || v.isBlank())) {
                throw new IllegalArgumentException("JDBC tableIds are required");
            }
            tableIds = List.copyOf(tableIds);
        }

        @Override public String sourceType() { return "JDBC_TABLE"; }
    }

    record JdbcSqlDefinition(Long datasourceId, String sql) implements DatasetSourceDefinition {
        public JdbcSqlDefinition {
            requireDatasource(datasourceId);
            if (sql == null || sql.isBlank()) throw new IllegalArgumentException("JDBC SQL is required");
        }

        @Override public String sourceType() { return "JDBC_SQL"; }
    }

    record AloudataViewDefinition(Long datasourceId, String analysisViewId) implements DatasetSourceDefinition {
        public AloudataViewDefinition {
            requireDatasource(datasourceId);
            requireText(analysisViewId, "Aloudata analysisViewId");
        }

        @Override public String sourceType() { return "ALOUDATA_ANALYSIS_VIEW"; }
    }

    record HttpApiDefinition(Long datasourceId, String apiDefinitionId) implements DatasetSourceDefinition {
        public HttpApiDefinition {
            requireDatasource(datasourceId);
            requireText(apiDefinitionId, "HTTP API definitionId");
        }

        @Override public String sourceType() { return "HTTP_API"; }
    }

    record FileDefinition(String objectId, String format, Integer schemaVersion) implements DatasetSourceDefinition {
        public FileDefinition {
            requireText(objectId, "file objectId");
            requireText(format, "file format");
            if (schemaVersion == null || schemaVersion <= 0) throw new IllegalArgumentException("file schemaVersion is required");
        }

        @Override public String sourceType() { return "FILE"; }
    }

    private static void requireDatasource(Long datasourceId) {
        if (datasourceId == null || datasourceId <= 0) throw new IllegalArgumentException("datasourceId is required");
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
    }
}
