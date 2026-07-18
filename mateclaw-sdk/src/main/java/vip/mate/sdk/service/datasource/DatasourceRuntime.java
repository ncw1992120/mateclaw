package vip.mate.sdk.service.datasource;

import vip.mate.datasource.model.DatasourceEntity;

import java.util.List;

/**
 * 数据源运行时接口
 * <p>
 * 提供数据源 CRUD、连接测试、启停等编程式访问能力。
 */
public interface DatasourceRuntime {

    /**
     * 列出所有数据源
     *
     * @return 数据源实体列表
     */
    List<DatasourceEntity> listDatasources();

    /**
     * 根据 ID 获取数据源
     *
     * @param id 数据源 ID
     * @return 数据源实体
     */
    DatasourceEntity getDatasource(Long id);

    /**
     * 测试数据源连接
     *
     * @param id 数据源 ID
     * @return 连接是否成功
     */
    boolean testDatasourceConnection(Long id);

    /**
     * 创建数据源
     *
     * @param entity 数据源实体
     * @return 创建后的数据源实体
     */
    DatasourceEntity createDatasource(DatasourceEntity entity);

    /**
     * 更新数据源
     *
     * @param entity 数据源实体（需包含 ID）
     * @return 更新后的数据源实体
     */
    DatasourceEntity updateDatasource(DatasourceEntity entity);

    /**
     * 删除数据源
     *
     * @param id 数据源 ID
     */
    void deleteDatasource(Long id);

    /**
     * 切换数据源启停状态
     *
     * @param id      数据源 ID
     * @param enabled 是否启用
     * @return 更新后的数据源实体
     */
    DatasourceEntity toggleDatasource(Long id, boolean enabled);
}
