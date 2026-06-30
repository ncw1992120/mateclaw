package vip.mate.dataagent.service;

import vip.mate.dataagent.dto.*;

import java.util.List;

/**
 * 数据源管理服务接口
 */
public interface DatasourceManageService {

    /**
     * 获取数据源列表
     * <p>
     * 按创建者用户 ID 过滤，不同用户仅可见自己配置的数据源。
     * 管理员也不可查看他人配置的数据源。
     *
     * @param ownerId 数据源创建者用户 ID，null 时不按 owner 过滤（仅供内部工具调用）
     * @return 数据源列表
     */
    List<DatasourceVO> listDatasources(Long ownerId);

    /**
     * 获取数据源列表（全量，不过滤 owner）
     * <p>
     * 仅供内部工具调用（如 DatasourceQueryTool、ChatScopeContext 装饰器），
     * 这些场景已通过用户勾选的白名单约束数据范围。
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
     * 创建数据源（带 ownerId）
     *
     * @param request 创建请求
     * @param ownerId 数据源创建者用户 ID
     * @return 创建后的数据源视图对象
     */
    DatasourceVO createDatasource(DatasourceCreateRequest request, Long ownerId);

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
     * 使用连接参数测试数据源连通性（不持久化数据源记录）
     *
     * @param request 连接参数
     * @return 连接是否成功
     */
    boolean testConnectionByParams(DatasourceCreateRequest request);

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