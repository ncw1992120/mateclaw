package vip.mate.dataagent.dataset.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.auth.crypto.AesPasswordCryptor;
import vip.mate.dataagent.dataset.*;
import vip.mate.dataagent.model.DatasetEntity;
import vip.mate.dataagent.model.DatasetFieldEntity;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.repository.DatasetFieldMapper;
import vip.mate.dataagent.repository.DatasetMapper;
import vip.mate.dataagent.repository.DatasourceMapper;
import vip.mate.dataagent.util.JdbcUtils;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/** JDBC 表和固化 SQL 数据集 Adapter；查询值只通过 PreparedStatement 绑定。 */
@Component
@RequiredArgsConstructor
public class JdbcDatasetAdapter implements DatasetSourceAdapter {
    private static final int QUERY_TIMEOUT_SECONDS = 60;
    private static final int MAX_PAGE_SIZE = 10_000;

    private final DatasetMapper datasetMapper;
    private final DatasetFieldMapper datasetFieldMapper;
    private final DatasourceMapper datasourceMapper;
    private final SqlValidationService sqlValidationService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(DatasetSourceType sourceType) {
        return sourceType == DatasetSourceType.JDBC_TABLE || sourceType == DatasetSourceType.JDBC_SQL;
    }

    @Override
    public DatasetInputDescriptor describe(DatasetAccessContext context, long datasetId) {
        DatasetEntity dataset = requireDataset(context, datasetId);
        List<DatasetColumn> columns = datasetFieldMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DatasetFieldEntity>()
                                .eq(DatasetFieldEntity::getDatasetId, datasetId)
                                .orderByAsc(DatasetFieldEntity::getOrdinalPosition))
                .stream()
                .map(field -> new DatasetColumn(field.getColumnName(), field.getColumnAlias(), field.getDataType(),
                        !Boolean.FALSE.equals(field.getNullable()), field.getFieldCategory()))
                .toList();
        return new DatasetInputDescriptor(datasetId, inputName(dataset), sourceType(dataset), columns,
                dataset.getRowCount(), Map.of("datasourceId", dataset.getDatasourceId()), null);
    }

    @Override
    public DatasetBatch read(DatasetAccessContext context, DatasetReadRequest request) {
        DatasetEntity dataset = requireDataset(context, request.datasetId());
        DatasourceEntity datasource = datasourceMapper.selectById(dataset.getDatasourceId());
        if (datasource == null) {
            throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE, "JDBC 数据源不存在");
        }
        List<String> columns = request.columns().isEmpty() ? fieldNames(request.datasetId()) : request.columns();
        CompiledJdbcQuery compiled = sqlValidationService.compile(baseSql(dataset, datasource), columns,
                request.filters(), Math.min(request.limit() == null ? 100 : request.limit(), MAX_PAGE_SIZE),
                request.offset() == null ? 0 : request.offset());
        try (Connection connection = DriverManager.getConnection(JdbcUtils.buildJdbcUrl(datasource),
                datasource.getUsername(), AesPasswordCryptor.decrypt(datasource.getPassword()));
             PreparedStatement statement = connection.prepareStatement(compiled.sql())) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            bind(statement, compiled.parameters());
            try (ResultSet resultSet = statement.executeQuery()) {
                ResultSetMetaData metadata = resultSet.getMetaData();
                int count = metadata.getColumnCount();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (resultSet.next() && rows.size() < MAX_PAGE_SIZE) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= count; i++) row.put(metadata.getColumnLabel(i), resultSet.getObject(i));
                    rows.add(row);
                }
                PushdownReport report = new PushdownReport(request.filters(), List.of(),
                        !columns.isEmpty(), true, compiled.digest());
                return new DatasetBatch(rows, null, rows.size(), true, report);
            }
        } catch (SQLTimeoutException e) {
            throw new DatasetReadException(DatasetReadErrorCode.SOURCE_TIMEOUT, "JDBC 查询超时", e);
        } catch (SQLException e) {
            throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE, "JDBC 查询失败", e);
        }
    }

    private DatasetEntity requireDataset(DatasetAccessContext context, long datasetId) {
        if (context == null || !context.canRead(datasetId))
            throw new DatasetReadException(DatasetReadErrorCode.ACCESS_DENIED, "无权读取数据集");
        DatasetEntity dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "数据集不存在: " + datasetId);
        DatasetSourceType type = sourceType(dataset);
        if (!supports(type)) throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "数据集不是 JDBC 类型");
        return dataset;
    }

    private String baseSql(DatasetEntity dataset, DatasourceEntity datasource) {
        if (sourceType(dataset) == DatasetSourceType.JDBC_SQL && dataset.getSourceConfig() != null) {
            try {
                Map<String, Object> config = objectMapper.readValue(dataset.getSourceConfig(), new TypeReference<>() {});
                Object sql = config.get("sql");
                if (sql != null && !String.valueOf(sql).isBlank()) return String.valueOf(sql);
            } catch (Exception e) {
                throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "source_config 不是合法 JSON", e);
            }
        }
        String table = Optional.ofNullable(dataset.getTableNames()).orElse("").split(",")[0].trim();
        if (table.isBlank()) throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "未配置 JDBC 表或 SQL");
        return "SELECT * FROM " + JdbcUtils.quoteIdentifier(datasource, table);
    }

    private List<String> fieldNames(long datasetId) {
        return datasetFieldMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DatasetFieldEntity>()
                        .eq(DatasetFieldEntity::getDatasetId, datasetId)
                        .orderByAsc(DatasetFieldEntity::getOrdinalPosition))
                .stream().map(DatasetFieldEntity::getColumnName).toList();
    }

    private void bind(PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int i = 0; i < parameters.size(); i++) statement.setObject(i + 1, parameters.get(i));
    }

    private DatasetSourceType sourceType(DatasetEntity dataset) {
        return DatasetSourceType.JDBC_SQL.name().equalsIgnoreCase(dataset.getSourceType())
                ? DatasetSourceType.JDBC_SQL : DatasetSourceType.JDBC_TABLE;
    }

    private String inputName(DatasetEntity dataset) {
        return dataset.getName() == null || dataset.getName().isBlank() ? "dataset-" + dataset.getId() : dataset.getName();
    }
}
