package vip.mate.dataagent.service;

import vip.mate.dataagent.dto.LogicalRelationCreateRequest;
import vip.mate.dataagent.dto.LogicalRelationUpdateRequest;
import vip.mate.dataagent.dto.LogicalRelationVO;

import java.util.List;

/**
 * 逻辑外键关系服务接口
 * <p>
 * 提供逻辑外键关系的 CRUD、按数据源/表查询等能力。
 * 逻辑外键弥补数据库物理外键缺失的问题，帮助 LLM 理解 JOIN 关系。
 */
public interface LogicalRelationService {

    /**
     * 按数据源查询所有逻辑外键关系
     *
     * @param datasourceId 数据源 ID
     * @return 逻辑外键关系列表
     */
    List<LogicalRelationVO> listByDatasourceId(Long datasourceId);

    /**
     * 按数据源和表名查询关联的逻辑外键关系
     * <p>
     * 同时匹配源表和目标表
     *
     * @param datasourceId 数据源 ID
     * @param tableNames   表名列表
     * @return 逻辑外键关系列表
     */
    List<LogicalRelationVO> listByDatasourceIdAndTableNames(Long datasourceId, List<String> tableNames);

    /**
     * 根据 ID 获取逻辑外键关系
     *
     * @param id 逻辑外键关系 ID
     * @return 逻辑外键关系视图对象
     */
    LogicalRelationVO getById(Long id);

    /**
     * 创建逻辑外键关系
     *
     * @param request 创建请求
     * @return 创建后的逻辑外键关系视图对象
     */
    LogicalRelationVO create(LogicalRelationCreateRequest request);

    /**
     * 更新逻辑外键关系
     *
     * @param id      逻辑外键关系 ID
     * @param request 更新请求
     * @return 更新后的逻辑外键关系视图对象
     */
    LogicalRelationVO update(Long id, LogicalRelationUpdateRequest request);

    /**
     * 删除逻辑外键关系
     *
     * @param id 逻辑外键关系 ID
     */
    void delete(Long id);

    /**
     * 从物理外键自动初始化逻辑外键关系
     * <p>
     * 基于已有的 DatasourceColumnEntity 中的 foreignKeyTable/foreignKeyColumn 信息，
     * 自动生成逻辑外键关系记录，仅创建不存在的记录。
     *
     * @param datasourceId 数据源 ID
     * @return 新创建的逻辑外键关系数量
     */
    int autoInitFromPhysicalForeignKeys(Long datasourceId);
}
