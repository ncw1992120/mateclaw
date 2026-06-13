package vip.mate.dataagent.service;

import vip.mate.dataagent.dto.*;

import java.util.List;

/**
 * 数据源管理服务接口
 */
public interface DatasourceManageService {

    /**
     * 获取所有数据源
     *
     * @return 数据源列表
     */
    List<DatasourceVO> listDatasources();

    /**
     * 根据 ID 获取数据源
     *
     * @param id 数据源 ID
     * @return 数据源视图对象
     */
    DatasourceVO getDatasource(Long id);

    /**
     * 创建数据源
     *
     * @param request 创建请求
     * @return 创建后的数据源视图对象
     */
    DatasourceVO createDatasource(DatasourceCreateRequest request);

    /**
     * 更新数据源
     *
     * @param id      数据源 ID
     * @param request 更新请求
     * @return 更新后的数据源视图对象
     */
    DatasourceVO updateDatasource(Long id, DatasourceUpdateRequest request);

    /**
     * 删除数据源
     *
     * @param id 数据源 ID
     */
    void deleteDatasource(Long id);

    /**
     * 测试数据源连接
     *
     * @param id 数据源 ID
     * @return 连接是否成功
     */
    boolean testConnection(Long id);

    /**
     * 切换数据源启停状态
     *
     * @param id      数据源 ID
     * @param enabled 是否启用
     * @return 更新后的数据源视图对象
     */
    DatasourceVO toggleDatasource(Long id, boolean enabled);

    /**
     * 触发 Schema 发现
     *
     * @param id 数据源 ID
     * @return 发现后的数据源视图对象
     */
    DatasourceVO triggerSchemaDiscovery(Long id);

    /**
     * 获取数据源下的表列表
     *
     * @param datasourceId 数据源 ID
     * @return 表列表
     */
    List<DatasourceTableVO> listTables(Long datasourceId);

    /**
     * 获取表详情（含字段列表）
     *
     * @param datasourceId 数据源 ID
     * @param tableId      表 ID
     * @return 表视图对象
     */
    DatasourceTableVO getTableDetail(Long datasourceId, Long tableId);

    /**
     * 获取表字段列表
     *
     * @param datasourceId 数据源 ID
     * @param tableId      表 ID
     * @return 字段列表
     */
    List<DatasourceColumnVO> listColumns(Long datasourceId, Long tableId);

    /**
     * 同步单张表的元数据
     *
     * @param datasourceId 数据源 ID
     * @param tableId      表 ID
     * @param mode         同步模式：append/overwrite
     * @return 更新后的表视图对象
     */
    DatasourceTableVO syncSingleTable(Long datasourceId, Long tableId, String mode);

    /**
     * 预览表数据
     *
     * @param datasourceId 数据源 ID
     * @param tableId      表 ID
     * @param limit        返回行数限制
     * @return 表数据预览视图对象
     */
    TableDataPreviewVO previewTableData(Long datasourceId, Long tableId, int limit);

    /**
     * 删除数据源下的表
     *
     * @param datasourceId 数据源 ID
     * @param tableId      表 ID
     */
    void deleteTable(Long datasourceId, Long tableId);
}