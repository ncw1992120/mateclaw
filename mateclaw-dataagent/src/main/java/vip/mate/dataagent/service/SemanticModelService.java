package vip.mate.dataagent.service;

import vip.mate.dataagent.dto.SemanticModelCreateRequest;
import vip.mate.dataagent.dto.SemanticModelUpdateRequest;
import vip.mate.dataagent.dto.SemanticModelVO;

import java.util.List;

/**
 * 字段级语义模型服务接口
 * <p>
 * 提供语义模型的 CRUD、批量操作、关键词搜索、按表查询等能力。
 */
public interface SemanticModelService {

    /**
     * 按数据源查询所有启用的语义模型
     *
     * @param datasourceId 数据源 ID
     * @return 语义模型列表
     */
    List<SemanticModelVO> listByDatasourceId(Long datasourceId);

    /**
     * 按数据源和表名查询启用的语义模型
     *
     * @param datasourceId 数据源 ID
     * @param tableNames   表名列表
     * @return 语义模型列表
     */
    List<SemanticModelVO> listByDatasourceIdAndTableNames(Long datasourceId, List<String> tableNames);

    /**
     * 根据 ID 获取语义模型
     *
     * @param id 语义模型 ID
     * @return 语义模型视图对象
     */
    SemanticModelVO getById(Long id);

    /**
     * 创建语义模型
     *
     * @param request 创建请求
     * @return 创建后的语义模型视图对象
     */
    SemanticModelVO create(SemanticModelCreateRequest request);

    /**
     * 更新语义模型
     *
     * @param id      语义模型 ID
     * @param request 更新请求
     * @return 更新后的语义模型视图对象
     */
    SemanticModelVO update(Long id, SemanticModelUpdateRequest request);

    /**
     * 删除语义模型
     *
     * @param id 语义模型 ID
     */
    void delete(Long id);

    /**
     * 启用语义模型
     *
     * @param id 语义模型 ID
     */
    void enable(Long id);

    /**
     * 停用语义模型
     *
     * @param id 语义模型 ID
     */
    void disable(Long id);

    /**
     * 关键词搜索语义模型
     *
     * @param datasourceId 数据源 ID
     * @param keyword      关键词
     * @return 匹配的语义模型列表
     */
    List<SemanticModelVO> searchByKeyword(Long datasourceId, String keyword);

    /**
     * 从物理 Schema 自动初始化语义模型
     * <p>
     * 基于已有的 DatasourceColumnEntity 自动生成基础语义模型记录，
     * 仅创建不存在的记录，已存在的不覆盖。
     *
     * @param datasourceId 数据源 ID
     * @return 新创建的语义模型数量
     */
    int autoInitFromSchema(Long datasourceId);

    /**
     * 从 Aloudata 指标平台同步语义模型
     * <p>
     * 将指标平台的指标和维度信息自动映射为语义模型：
     * <ul>
     *   <li>指标 → tableName=指标类目, columnName=metricName, businessName=metricDisplayName,
     *       businessDescription=businessCaliber, synonyms=同义词, unit=单位</li>
     *   <li>维度 → tableName=维度类目, columnName=dimName, businessName=dimDisplayName,
     *       businessDescription=dimDescription, synonyms=同义词</li>
     * </ul>
     * 仅创建不存在的记录，已存在的不覆盖。
     *
     * @param datasourceId 数据源 ID（必须为 aloudata 类型）
     * @return 新创建的语义模型数量
     */
    int syncFromAloudata(Long datasourceId);
}
