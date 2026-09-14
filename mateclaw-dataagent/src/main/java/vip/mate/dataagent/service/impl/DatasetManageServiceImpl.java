package vip.mate.dataagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mate.dataagent.auth.crypto.AesPasswordCryptor;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dataset.DatasetAccessContext;
import vip.mate.dataagent.dataset.DatasetColumn;
import vip.mate.dataagent.dataset.DatasetInputDescriptor;
import vip.mate.dataagent.dataset.DatasetSourceType;
import vip.mate.dataagent.dto.*;
import vip.mate.dataagent.exception.BusinessException;
import vip.mate.dataagent.model.*;
import vip.mate.dataagent.repository.*;
import vip.mate.dataagent.service.DatasetManageService;
import vip.mate.dataagent.service.DatasourceManageService;
import vip.mate.dataagent.util.JdbcUtils;

import java.sql.*;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DatasetManageServiceImpl implements DatasetManageService {

    private static final Logger log = LoggerFactory.getLogger(DatasetManageServiceImpl.class);

    private final DatasetMapper datasetMapper;
    private final DatasetFieldMapper datasetFieldMapper;
    private final DatasetDataMapper datasetDataMapper;
    private final DatasourceMapper datasourceMapper;
    private final DatasourceTableMapper datasourceTableMapper;
    private final DatasourceColumnMapper datasourceColumnMapper;
    private final ObjectMapper objectMapper;
    private final WorkspaceGuard workspaceGuard;
    private final DatasourceManageService datasourceManageService;

    private static final Set<String> NUMERIC_TYPES = Set.of(
            "int", "bigint", "smallint", "tinyint", "decimal", "float", "double",
            "numeric", "real", "integer", "number", "money"
    );

    private static final int DEFAULT_COLUMN_WIDTH = 150;

    @Override
    public DatasetInputDescriptor getInputDescriptor(DatasetAccessContext context, Long datasetId, String inputName) {
        if (context == null || datasetId == null || !context.canRead(datasetId)) {
            throw new BusinessException(403, "无权访问该数据集");
        }
        if (!Objects.equals(context.workspaceId(), workspaceGuard.currentWorkspaceId())
                || !Objects.equals(context.userId(), workspaceGuard.currentUserId())) {
            throw new BusinessException(403, "工作区上下文不匹配");
        }
        DatasetEntity entity = datasetMapper.selectById(datasetId);
        if (entity == null) {
            throw new BusinessException(404, "数据集不存在: " + datasetId);
        }
        String resolvedInputName = inputName == null || inputName.isBlank()
                ? entity.getName() : inputName;
        List<DatasetColumn> columns = listFields(datasetId).stream()
                .map(field -> new DatasetColumn(field.getColumnName(),
                        field.getColumnAlias(), field.getDataType(),
                        !Boolean.FALSE.equals(field.getNullable()),
                        field.getFieldCategory()))
                .toList();
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (entity.getDatasourceId() != null) metadata.put("datasourceId", entity.getDatasourceId());
        if (entity.getTableNames() != null) metadata.put("tableNames", entity.getTableNames());
        return new DatasetInputDescriptor(
                datasetId,
                resolvedInputName,
                resolveSourceType(entity.getSourceType()),
                columns,
                entity.getRowCount(),
                metadata,
                null);
    }

    private DatasetSourceType resolveSourceType(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return DatasetSourceType.JDBC_TABLE;
        }
        try {
            return DatasetSourceType.valueOf(sourceType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            String normalized = sourceType.toLowerCase(Locale.ROOT);
            if (normalized.contains("aloudata") || normalized.contains("analysis_view")) {
                return DatasetSourceType.ALOUDATA_ANALYSIS_VIEW;
            }
            if (normalized.equals("api") || normalized.equals("http") || normalized.equals("http_api")) {
                return DatasetSourceType.HTTP_API;
            }
            if (Set.of("csv", "excel", "parquet", "file").contains(normalized)) {
                return DatasetSourceType.FILE;
            }
            return DatasetSourceType.JDBC_TABLE;
        }
    }

    @Override
    public List<DatasetVO> listDatasets() {
        LambdaQueryWrapper<DatasetEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasetEntity::getWorkspaceId, workspaceGuard.currentWorkspaceId());
        wrapper.orderByDesc(DatasetEntity::getUpdateTime);
        List<DatasetEntity> entities = datasetMapper.selectList(wrapper);
        return entities.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public DatasetVO getDataset(Long id) {
        requireDatasetAccess(id);
        DatasetEntity entity = datasetMapper.selectById(id);
        DatasetVO vo = toVO(entity);
        vo.setFields(listFields(id));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DatasetVO createDataset(DatasetCreateRequest request) {
        DatasetSourceDefinition typedDefinition = request.getSourceDefinition();
        String normalizedName = normalizeDatasetName(request.getName());
        ensureUniqueDatasetName(normalizedName, workspaceGuard.currentWorkspaceId(), null);
        // Keep the nullable branch boxed: FILE datasets intentionally have no datasource id.
        Long datasourceIdLong = typedDefinition == null
                ? Long.valueOf(request.getDatasourceId())
                : typedDatasourceId(typedDefinition);
        DatasourceEntity dsEntity = datasourceIdLong == null ? null : datasourceMapper.selectById(datasourceIdLong);
        if (typedDefinition == null && dsEntity == null) {
            throw new RuntimeException("数据源不存在");
        }
        if (typedDefinition != null && datasourceIdLong != null && dsEntity == null) {
            throw new RuntimeException("数据源不存在");
        }
        validateDatasourceCompatibility(typedDefinition, dsEntity);
        if (datasourceIdLong != null) {
            datasourceManageService.checkDatasourceReadable(datasourceIdLong);
        }
        DatasetEntity entity = new DatasetEntity();
        entity.setName(normalizedName);
        entity.setDescription(request.getDescription());
        entity.setDatasourceId(datasourceIdLong);
        entity.setSourceType(typedDefinition == null ? DatasetSourceType.JDBC_TABLE.name() : typedDefinition.sourceType());
        entity.setSourceConfig(serializeSourceDefinition(typedDefinition));
        entity.setSchemaVersion(1);
        entity.setDatasourceName(dsEntity == null ? null : dsEntity.getName());
        entity.setStatus(DataAgentConstants.DATASET_STATUS_DRAFT);
        entity.setDeleted(0);
        entity.setWorkspaceId(workspaceGuard.currentWorkspaceId());
        entity.setOwnerId(workspaceGuard.currentUserId());
        List<String> requestedTableIds = typedTableIds(typedDefinition, request.getTableIds());
        if (requestedTableIds != null && !requestedTableIds.isEmpty()) {
            List<Long> tableIdLongs = requestedTableIds.stream()
                    .map(Long::parseLong)
                    .toList();
            entity.setTableIds(tableIdLongs.stream().map(String::valueOf).collect(Collectors.joining(",")));
            List<String> tableNames = new ArrayList<>();
            for (Long tableId : tableIdLongs) {
                DatasourceTableEntity tableEntity = datasourceTableMapper.selectById(tableId);
                if (tableEntity != null) {
                    tableNames.add(tableEntity.getTableName());
                }
            }
            entity.setTableNames(String.join(",", tableNames));
        } else {
            entity.setTableIds("");
            entity.setTableNames("");
        }
        entity.setColumnCount(0);
        entity.setRowCount(0L);
        datasetMapper.insert(entity);
        if (requestedTableIds != null && !requestedTableIds.isEmpty()) {
            List<Long> tableIdLongs = requestedTableIds.stream()
                    .map(Long::parseLong)
                    .toList();
            for (Long tableId : tableIdLongs) {
                importFieldsFromTable(entity.getId(), datasourceIdLong, tableId);
            }
            Long fieldCount = countFields(entity.getId());
            entity.setColumnCount(fieldCount.intValue());
            datasetMapper.updateById(entity);
        }
        return toVO(entity);
    }

    private Long typedDatasourceId(DatasetSourceDefinition definition) {
        return switch (definition) {
            case DatasetSourceDefinition.JdbcTableDefinition value -> value.datasourceId();
            case DatasetSourceDefinition.JdbcSqlDefinition value -> value.datasourceId();
            case DatasetSourceDefinition.AloudataViewDefinition value -> value.datasourceId();
            case DatasetSourceDefinition.HttpApiDefinition value -> value.datasourceId();
            case DatasetSourceDefinition.FileDefinition ignored -> null;
        };
    }

    private List<String> typedTableIds(DatasetSourceDefinition definition, List<String> legacyTableIds) {
        if (definition instanceof DatasetSourceDefinition.JdbcTableDefinition value) return value.tableIds();
        return definition == null ? legacyTableIds : List.of();
    }

    private String serializeSourceDefinition(DatasetSourceDefinition definition) {
        if (definition == null) return null;
        try {
            if (definition instanceof DatasetSourceDefinition.FileDefinition file) {
                // File uploads are durable workspace objects, while ObjectRef is the existing
                // execution-time transport contract. Encode a managed durable reference here
                // so old readers remain compatible without exposing a local path or URL.
                Map<String, Object> ref = new LinkedHashMap<>();
                ref.put("objectId", file.objectId());
                ref.put("workspaceId", workspaceGuard.currentWorkspaceId());
                ref.put("taskId", "dataset-" + workspaceGuard.currentWorkspaceId());
                ref.put("format", file.format());
                ref.put("digest", "sha256:managed-file");
                ref.put("expiresAt", Long.MAX_VALUE);
                Map<String, Object> stored = new LinkedHashMap<>();
                stored.put("objectRef", ref);
                stored.put("fileName", "dataset." + file.format().toLowerCase(Locale.ROOT));
                stored.put("format", file.format());
                stored.put("schemaVersion", file.schemaVersion());
                return objectMapper.writeValueAsString(stored);
            }
            if (definition instanceof DatasetSourceDefinition.HttpApiDefinition http) {
                DatasourceEntity datasource = datasourceMapper.selectById(http.datasourceId());
                if (datasource == null || datasource.getConnectionParams() == null
                        || datasource.getConnectionParams().isBlank()) {
                    throw new IllegalArgumentException("HTTP API 登记定义不存在: " + http.apiDefinitionId());
                }
                Map<String, Object> connectionParams = objectMapper.readValue(
                        datasource.getConnectionParams(), new TypeReference<LinkedHashMap<String, Object>>() {});
                Map<String, Object> definitions = objectMapper.convertValue(
                        connectionParams.getOrDefault("apiDefinitions", Map.of()),
                        new TypeReference<LinkedHashMap<String, Object>>() {});
                Object registered = definitions.get(http.apiDefinitionId());
                if (!(registered instanceof Map<?, ?>)) {
                    throw new IllegalArgumentException("HTTP API 登记定义不存在: " + http.apiDefinitionId());
                }
                Map<String, Object> resolved = objectMapper.convertValue(
                        registered, new TypeReference<LinkedHashMap<String, Object>>() {});
                resolved.put("sourceType", http.sourceType());
                resolved.put("apiDefinitionId", http.apiDefinitionId());
                return objectMapper.writeValueAsString(resolved);
            }
            return objectMapper.writeValueAsString(definition);
        }
        catch (Exception e) { throw new RuntimeException("数据集来源定义无效", e); }
    }

    /**
     * 防止把 JDBC/Aloudata 专属来源绑定到错误的连接类型。
     * HTTP/API 仍允许复用已有连接记录中的登记定义，FILE 没有连接 ID。
     */
    private void validateDatasourceCompatibility(DatasetSourceDefinition definition, DatasourceEntity datasource) {
        if (definition == null || datasource == null || datasource.getSourceType() == null) return;
        String datasourceType = datasource.getSourceType().trim().toLowerCase(Locale.ROOT);
        boolean aloudata = datasourceType.contains("aloudata") || datasourceType.contains("analysis_view");
        boolean httpApi = datasourceType.equals("api")
                || datasourceType.equals("http")
                || datasourceType.equals("http_api")
                || datasourceType.contains("http-api");
        if ((definition instanceof DatasetSourceDefinition.JdbcTableDefinition
                || definition instanceof DatasetSourceDefinition.JdbcSqlDefinition) && aloudata) {
            throw new IllegalArgumentException("JDBC 数据集不能绑定 Aloudata 数据源");
        }
        if (definition instanceof DatasetSourceDefinition.AloudataViewDefinition && !aloudata) {
            throw new IllegalArgumentException("Aloudata 指标视图必须绑定 Aloudata 数据源");
        }
        if (definition instanceof DatasetSourceDefinition.HttpApiDefinition && !httpApi) {
            throw new IllegalArgumentException("HTTP API 数据集必须绑定 HTTP/API 数据源");
        }
    }

    private String normalizeDatasetName(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("数据集名称不能为空");
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC).trim();
        if (normalized.isBlank()) throw new IllegalArgumentException("数据集名称不能为空");
        return normalized;
    }

    private void ensureUniqueDatasetName(String name, Long workspaceId, Long excludeId) {
        LambdaQueryWrapper<DatasetEntity> wrapper = new LambdaQueryWrapper<DatasetEntity>()
                .eq(DatasetEntity::getWorkspaceId, workspaceId)
                .eq(DatasetEntity::getName, name);
        if (excludeId != null) wrapper.ne(DatasetEntity::getId, excludeId);
        if (datasetMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException("同一工作区内数据集名称已存在: " + name);
        }
    }

    @Override
    public DatasetVO updateDataset(Long id, DatasetUpdateRequest request) {
        requireDatasetManageOwnership(id);
        DatasetEntity entity = datasetMapper.selectById(id);
        if (request.getName() != null) {
            String normalizedName = normalizeDatasetName(request.getName());
            ensureUniqueDatasetName(normalizedName, entity.getWorkspaceId(), id);
            entity.setName(normalizedName);
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getSourceDefinition() != null) {
            DatasetSourceDefinition definition = request.getSourceDefinition();
            Long datasourceId = typedDatasourceId(definition);
            if (datasourceId != null && datasourceMapper.selectById(datasourceId) == null) {
                throw new RuntimeException("数据源不存在");
            }
            validateDatasourceCompatibility(definition,
                    datasourceId == null ? null : datasourceMapper.selectById(datasourceId));
            if (datasourceId != null) {
                datasourceManageService.checkDatasourceReadable(datasourceId);
            }
            entity.setDatasourceId(datasourceId);
            entity.setDatasourceName(datasourceId == null ? null : datasourceMapper.selectById(datasourceId).getName());
            entity.setSourceType(definition.sourceType());
            entity.setSourceConfig(serializeSourceDefinition(definition));
            List<String> tableIds = typedTableIds(definition, null);
            entity.setTableIds(tableIds == null ? "" : String.join(",", tableIds));
            entity.setTableNames("");
        }
        datasetMapper.updateById(entity);
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDataset(Long id) {
        requireDatasetManageOwnership(id);
        datasetMapper.deleteById(id);
        LambdaQueryWrapper<DatasetFieldEntity> fieldWrapper = new LambdaQueryWrapper<>();
        fieldWrapper.eq(DatasetFieldEntity::getDatasetId, id);
        datasetFieldMapper.delete(fieldWrapper);
        LambdaQueryWrapper<DatasetDataEntity> dataWrapper = new LambdaQueryWrapper<>();
        dataWrapper.eq(DatasetDataEntity::getDatasetId, id);
        datasetDataMapper.delete(dataWrapper);
    }

    @Override
    public List<DatasetFieldVO> listFields(Long datasetId) {
        requireDatasetManageOwnership(datasetId);
        LambdaQueryWrapper<DatasetFieldEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasetFieldEntity::getDatasetId, datasetId);
        wrapper.orderByAsc(DatasetFieldEntity::getOrdinalPosition);
        List<DatasetFieldEntity> entities = datasetFieldMapper.selectList(wrapper);
        return entities.stream().map(this::toFieldVO).collect(Collectors.toList());
    }

    @Override
    public DatasetDataVO getDatasetData(Long datasetId, int page, int size) {
        requireDatasetManageOwnership(datasetId);
        DatasetEntity datasetEntity = datasetMapper.selectById(datasetId);
        List<DatasetFieldVO> fields = listFields(datasetId);
        DatasetDataVO result = new DatasetDataVO();
        List<DatasetDataVO.DatasetColumnDef> columnDefs = new ArrayList<>();
        for (DatasetFieldVO field : fields) {
            DatasetDataVO.DatasetColumnDef colDef = new DatasetDataVO.DatasetColumnDef();
            colDef.setName(field.getColumnName());
            colDef.setTitle(field.getColumnAlias() != null ? field.getColumnAlias() : field.getColumnName());
            colDef.setDataType(field.getDataType());
            colDef.setFieldCategory(field.getFieldCategory());
            colDef.setEditable(true);
            colDef.setWidth(DEFAULT_COLUMN_WIDTH);
            columnDefs.add(colDef);
        }
        result.setColumns(columnDefs);
        LambdaQueryWrapper<DatasetDataEntity> dataWrapper = new LambdaQueryWrapper<>();
        dataWrapper.eq(DatasetDataEntity::getDatasetId, datasetId);
        dataWrapper.orderByAsc(DatasetDataEntity::getId);
        long total = datasetDataMapper.selectCount(dataWrapper);
        result.setTotal(total);
        dataWrapper.last("LIMIT " + size + " OFFSET " + (page - 1) * size);
        List<DatasetDataEntity> dataEntities = datasetDataMapper.selectList(dataWrapper);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DatasetDataEntity dataEntity : dataEntities) {
            try {
                Map<String, Object> row = objectMapper.readValue(
                        dataEntity.getRowData(), new TypeReference<LinkedHashMap<String, Object>>() {});
                row.put("_rowId", String.valueOf(dataEntity.getId()));
                rows.add(row);
            } catch (Exception e) {
                log.warn("解析行数据失败, dataId={}: {}", dataEntity.getId(), e.getMessage());
            }
        }
        result.setRows(rows);
        datasetEntity.setRowCount(total);
        datasetMapper.updateById(datasetEntity);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRow(Long datasetId, DatasetRowUpdateRequest request) {
        requireDatasetManageOwnership(datasetId);
        Map<String, Object> rowKey = request.getRowKey();
        Map<String, Object> values = request.getValues();
        DatasetDataEntity targetEntity = findDataEntityByRowKey(datasetId, rowKey);
        if (targetEntity == null) {
            throw new RuntimeException("未找到匹配的数据行");
        }
        try {
            Map<String, Object> existingRow = objectMapper.readValue(
                    targetEntity.getRowData(), new TypeReference<LinkedHashMap<String, Object>>() {});
            existingRow.putAll(values);
            existingRow.remove("_rowId");
            String updatedJson = objectMapper.writeValueAsString(existingRow);
            targetEntity.setRowData(updatedJson);
            datasetDataMapper.updateById(targetEntity);
        } catch (Exception e) {
            throw new RuntimeException("更新行数据失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addRow(Long datasetId, DatasetRowCreateRequest request) {
        requireDatasetManageOwnership(datasetId);
        try {
            String json = objectMapper.writeValueAsString(request.getValues());
            DatasetDataEntity entity = new DatasetDataEntity();
            entity.setDatasetId(datasetId);
            entity.setRowData(json);
            entity.setDeleted(0);
            datasetDataMapper.insert(entity);
            DatasetEntity datasetEntity = datasetMapper.selectById(datasetId);
            if (datasetEntity != null) {
                LambdaQueryWrapper<DatasetDataEntity> countWrapper = new LambdaQueryWrapper<>();
                countWrapper.eq(DatasetDataEntity::getDatasetId, datasetId);
                long rowCount = datasetDataMapper.selectCount(countWrapper);
                datasetEntity.setRowCount(rowCount);
                datasetMapper.updateById(datasetEntity);
            }
        } catch (Exception e) {
            throw new RuntimeException("新增数据行失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRow(Long datasetId, Map<String, Object> rowKey) {
        requireDatasetManageOwnership(datasetId);
        DatasetDataEntity targetEntity = findDataEntityByRowKey(datasetId, rowKey);
        if (targetEntity != null) {
            datasetDataMapper.deleteById(targetEntity.getId());
        }
        DatasetEntity datasetEntity = datasetMapper.selectById(datasetId);
        if (datasetEntity != null) {
            LambdaQueryWrapper<DatasetDataEntity> countWrapper = new LambdaQueryWrapper<>();
            countWrapper.eq(DatasetDataEntity::getDatasetId, datasetId);
            long rowCount = datasetDataMapper.selectCount(countWrapper);
            datasetEntity.setRowCount(rowCount);
            datasetMapper.updateById(datasetEntity);
        }
    }

    @Override
    public DatasetFieldVO updateFieldCategory(Long fieldId, String fieldCategory) {
        DatasetFieldEntity entity = datasetFieldMapper.selectById(fieldId);
        if (entity == null) {
            throw new RuntimeException("字段不存在");
        }
        requireDatasetManageOwnership(entity.getDatasetId());
        entity.setFieldCategory(fieldCategory);
        datasetFieldMapper.updateById(entity);
        return toFieldVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DatasetVO syncDatasetData(Long datasetId) {
        requireDatasetManageOwnership(datasetId);
        DatasetEntity datasetEntity = datasetMapper.selectById(datasetId);
        DatasourceEntity dsEntity = datasourceMapper.selectById(datasetEntity.getDatasourceId());
        if (dsEntity == null) {
            throw new RuntimeException("关联数据源不存在");
        }
        LambdaQueryWrapper<DatasetDataEntity> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(DatasetDataEntity::getDatasetId, datasetId);
        datasetDataMapper.delete(deleteWrapper);
        String tableNamesStr = datasetEntity.getTableNames();
        if (tableNamesStr == null || tableNamesStr.isEmpty()) {
            datasetEntity.setStatus(DataAgentConstants.DATASET_STATUS_READY);
            datasetEntity.setRowCount(0L);
            datasetMapper.updateById(datasetEntity);
            return toVO(datasetEntity);
        }
        String firstTable = tableNamesStr.split(",")[0];
        List<DatasetFieldVO> fields = listFields(datasetId);
        int rowNum = 0;
        try (Connection conn = DriverManager.getConnection(
                JdbcUtils.buildJdbcUrl(dsEntity), dsEntity.getUsername(),
                AesPasswordCryptor.decrypt(dsEntity.getPassword()))) {
            List<Map<String, Object>> sourceRows = queryAllTableData(conn, dsEntity, firstTable, fields);
            for (Map<String, Object> row : sourceRows) {
                DatasetDataEntity dataEntity = new DatasetDataEntity();
                dataEntity.setDatasetId(datasetId);
                dataEntity.setRowData(objectMapper.writeValueAsString(row));
                dataEntity.setSourceRowNumber(++rowNum);
                dataEntity.setDeleted(0);
                datasetDataMapper.insert(dataEntity);
            }
            datasetEntity.setRowCount((long) rowNum);
            datasetEntity.setStatus(DataAgentConstants.DATASET_STATUS_READY);
        } catch (Exception e) {
            log.error("同步数据集数据失败: {}", e.getMessage(), e);
            datasetEntity.setStatus(DataAgentConstants.DATASET_STATUS_ERROR);
            datasetEntity.setRowCount(0L);
        }
        datasetMapper.updateById(datasetEntity);
        return toVO(datasetEntity);
    }

    /**
     * 校验当前用户对指定数据集是否具有访问权限
     * <p>
     * 校验数据集存在性 + workspaceId 一致性，不匹配抛出 BusinessException。
     */
    /**
     * 访问级校验：数据集存在性 + workspaceId 一致性（读操作即可调用）。
     */
    private void requireDatasetAccess(Long id) {
        DatasetEntity entity = datasetMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "数据集不存在: " + id);
        }
        Long currentWorkspaceId = workspaceGuard.currentWorkspaceId();
        if (entity.getWorkspaceId() == null
                || !entity.getWorkspaceId().equals(currentWorkspaceId)) {
            throw new BusinessException(403, "无权访问该数据集");
        }
    }

    /**
     * 管理级校验（写操作）：在访问级校验之上，要求当前用户为
     * 创建者本人 或 工作区 admin/owner；历史无主数据仅管理员层级可维护。
     */
    private void requireDatasetManageOwnership(Long id) {
        requireDatasetAccess(id);
        DatasetEntity entity = datasetMapper.selectById(id);
        workspaceGuard.requireResourceOwner(entity == null ? null : entity.getOwnerId());
    }

    private DatasetDataEntity findDataEntityByRowKey(Long datasetId, Map<String, Object> rowKey) {
        Object rowIdObj = rowKey.get("_rowId");
        if (rowIdObj != null) {
            try {
                Long rowId = Long.parseLong(String.valueOf(rowIdObj));
                DatasetDataEntity entity = datasetDataMapper.selectById(rowId);
                if (entity != null && entity.getDatasetId().equals(datasetId) && entity.getDeleted() == 0) {
                    return entity;
                }
            } catch (NumberFormatException e) {
                log.warn("_rowId 格式无效: {}", rowIdObj);
            }
        }
        LambdaQueryWrapper<DatasetDataEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasetDataEntity::getDatasetId, datasetId);
        List<DatasetDataEntity> allRows = datasetDataMapper.selectList(wrapper);
        for (DatasetDataEntity de : allRows) {
            try {
                Map<String, Object> row = objectMapper.readValue(
                        de.getRowData(), new TypeReference<LinkedHashMap<String, Object>>() {});
                boolean match = true;
                for (Map.Entry<String, Object> entry : rowKey.entrySet()) {
                    if ("_rowId".equals(entry.getKey())) {
                        continue;
                    }
                    Object rowVal = row.get(entry.getKey());
                    if (!Objects.equals(String.valueOf(rowVal), String.valueOf(entry.getValue()))) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return de;
                }
            } catch (Exception e) {
                log.warn("匹配行数据失败, dataId={}: {}", de.getId(), e.getMessage());
            }
        }
        return null;
    }

    private void importFieldsFromTable(Long datasetId, Long datasourceId, Long tableId) {
        LambdaQueryWrapper<DatasourceColumnEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasourceColumnEntity::getDatasourceId, datasourceId);
        wrapper.eq(DatasourceColumnEntity::getTableId, tableId);
        wrapper.orderByAsc(DatasourceColumnEntity::getOrdinalPosition);
        List<DatasourceColumnEntity> columns = datasourceColumnMapper.selectList(wrapper);
        DatasourceTableEntity tableEntity = datasourceTableMapper.selectById(tableId);
        String sourceTableName = tableEntity != null ? tableEntity.getTableName() : "";
        int ordinal = 1;
        for (DatasourceColumnEntity col : columns) {
            DatasetFieldEntity fieldEntity = new DatasetFieldEntity();
            fieldEntity.setDatasetId(datasetId);
            fieldEntity.setColumnName(col.getColumnName());
            fieldEntity.setColumnAlias(col.getColumnComment());
            fieldEntity.setColumnComment(col.getColumnComment());
            fieldEntity.setDataType(col.getDataType());
            fieldEntity.setColumnSize(col.getColumnSize());
            fieldEntity.setDecimalDigits(col.getDecimalDigits());
            fieldEntity.setFieldCategory(classifyField(col.getDataType()));
            fieldEntity.setPrimaryKey(col.getPrimaryKey());
            fieldEntity.setNullable(col.getNullable());
            fieldEntity.setDefaultValue(col.getDefaultValue());
            fieldEntity.setOrdinalPosition(ordinal++);
            fieldEntity.setDatasourceId(datasourceId);
            fieldEntity.setSourceTableId(tableId);
            fieldEntity.setSourceTableName(sourceTableName);
            fieldEntity.setDeleted(0);
            datasetFieldMapper.insert(fieldEntity);
        }
    }

    private String classifyField(String dataType) {
        if (dataType == null) {
            return DataAgentConstants.FIELD_CATEGORY_DIMENSION;
        }
        String lower = dataType.toLowerCase();
        for (String numericType : NUMERIC_TYPES) {
            if (lower.contains(numericType)) {
                return DataAgentConstants.FIELD_CATEGORY_MEASURE;
            }
        }
        return DataAgentConstants.FIELD_CATEGORY_DIMENSION;
    }

    private Long countFields(Long datasetId) {
        LambdaQueryWrapper<DatasetFieldEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasetFieldEntity::getDatasetId, datasetId);
        return datasetFieldMapper.selectCount(wrapper);
    }

    private List<Map<String, Object>> queryAllTableData(Connection conn, DatasourceEntity dsEntity,
                                                         String tableName, List<DatasetFieldVO> fields) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT ");
        if (fields.isEmpty()) {
            sql.append("*");
        } else {
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append(JdbcUtils.quoteIdentifier(dsEntity, fields.get(i).getColumnName()));
            }
        }
        sql.append(" FROM ").append(JdbcUtils.quoteIdentifier(dsEntity, tableName));
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql.toString())) {
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    String colLabel = meta.getColumnLabel(i);
                    Object value = rs.getObject(i);
                    row.put(colLabel, value);
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private DatasetVO toVO(DatasetEntity entity) {
        DatasetVO vo = new DatasetVO();
        BeanUtils.copyProperties(entity, vo);
        if (entity.getCreateTime() != null) {
            vo.setCreateTime(entity.getCreateTime().toString());
        }
        if (entity.getUpdateTime() != null) {
            vo.setUpdateTime(entity.getUpdateTime().toString());
        }
        return vo;
    }

    private DatasetFieldVO toFieldVO(DatasetFieldEntity entity) {
        DatasetFieldVO vo = new DatasetFieldVO();
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
