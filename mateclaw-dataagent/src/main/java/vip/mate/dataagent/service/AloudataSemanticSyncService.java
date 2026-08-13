package vip.mate.dataagent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import vip.mate.dataagent.dto.*;
import vip.mate.dataagent.model.AloudataCategoryEntity;
import vip.mate.dataagent.model.AloudataDimensionEntity;
import vip.mate.dataagent.model.AloudataMetricEntity;

import java.util.List;

/**
 * Aloudata 语义层同步服务接口
 * <p>
 * 负责从 Aloudata 指标平台同步指标、维度、类目元数据到本地 MySQL + ES。
 * 优化 N+1 调用：使用 metric_batch_detail 批量获取指标详情，
 * 批量调用 metric_available_dimensions 建立指标-维度关联。
 */
public interface AloudataSemanticSyncService {

    /**
     * 同步结果
     */
    record SyncResult(
            int metricCount,
            int dimensionCount,
            int metricDimensionCount,
            int categoryCount,
            long elapsedMs,
            String status,
            String message
    ) {}

    /**
     * 全量同步 Aloudata 元数据到本地语义层
     * <p>
     * 流程：
     * 1. category_list → 类目映射
     * 2. metrics_list (分页) → 指标列表
     * 3. metric_batch_detail (1次) → 批量获取同义词
     * 4. metric_available_dimensions (批量) → 指标-维度关联
     * 5. dimensions_list (分页) → 维度列表
     * 6. dimension_detail (逐个) → 维度同义词
     * 7. 向量化 + ES 索引
     *
     * @param datasourceId 本地数据源 ID
     * @return 同步结果
     */
    SyncResult fullSync(Long datasourceId);

    /**
     * 查询已同步的指标列表（分页，支持关键字搜索）
     *
     * @param datasourceId 数据源 ID
     * @param pageNumber   页码
     * @param pageSize     每页大小
     * @param keyword      搜索关键字（匹配名称、展示名、同义词），可为 null
     */
    List<AloudataMetricSemanticDTO> listSyncedMetrics(Long datasourceId, int pageNumber, int pageSize, String keyword);

    /**
     * 查询已同步的维度列表（分页，支持关键字搜索）
     *
     * @param datasourceId 数据源 ID
     * @param pageNumber   页码
     * @param pageSize     每页大小
     * @param keyword      搜索关键字（匹配名称、展示名、同义词），可为 null
     */
    List<AloudataDimensionSemanticDTO> listSyncedDimensions(Long datasourceId, int pageNumber, int pageSize, String keyword);

    /**
     * 按关键词分页查询指标实体
     * <p>
     * 复用公共的 LIKE 查询逻辑，供同数据源分页查询与跨数据源候选查询使用；
     * datasourceId 为空时表示跨数据源查询（如业务词典关联引用候选选择）。
     *
     * @param datasourceId 数据源 ID，为空表示跨数据源
     * @param keyword      搜索关键字（匹配名称、展示名、同义词），可为 null
     * @param offset       偏移量
     * @param limit        返回条数上限
     * @return 指标实体列表
     */
    List<AloudataMetricEntity> pageMetricEntities(Long datasourceId, String keyword, int offset, int limit);

    /**
     * 按关键词分页查询维度实体
     * <p>
     * 复用公共的 LIKE 查询逻辑，供同数据源分页查询与跨数据源候选查询使用；
     * datasourceId 为空时表示跨数据源查询（如业务词典关联引用候选选择）。
     *
     * @param datasourceId 数据源 ID，为空表示跨数据源
     * @param keyword      搜索关键字（匹配名称、展示名、同义词），可为 null
     * @param offset       偏移量
     * @param limit        返回条数上限
     * @return 维度实体列表
     */
    List<AloudataDimensionEntity> pageDimensionEntities(Long datasourceId, String keyword, int offset, int limit);

    /**
     * 查询指标关联的维度名称列表
     */
    List<String> listMetricDimensions(Long datasourceId, String metricName);

    /**
     * 查询指标关联的维度详情列表
     */
    List<AloudataDimensionSemanticDTO> listMetricDimensionDetails(Long datasourceId, String metricName);

    /**
     * 批量查询多个指标关联的维度详情列表（去重合并，支持关键字过滤）
     *
     * @param datasourceId 数据源 ID
     * @param metricNames  指标英文名列表
     * @param keyword      搜索关键字（匹配维度名称、展示名），可为 null
     * @return 去重后的维度详情列表
     */
    List<AloudataDimensionSemanticDTO> listMetricsDimensionDetails(Long datasourceId, List<String> metricNames, String keyword);

    /**
     * 查询维度关联的指标详情列表
     */
    List<AloudataMetricSemanticDTO> listDimensionMetricDetails(Long datasourceId, String dimName);

    /**
     * 查询维度的可选值列表
     * <p>
     * 通过维度关联的指标调用 Aloudata 指标查询 API，获取该维度的去重值列表。
     *
     * @param datasourceId 数据源 ID
     * @param dimName      维度英文名
     * @param keyword      搜索关键字（匹配维度值），可为 null
     * @param limit        最大返回条数
     * @return 维度值列表
     */
    List<String> listDimensionValues(Long datasourceId, String dimName, String keyword, int limit);

    /**
     * 查询同步状态
     */
    SyncResult getSyncStatus(Long datasourceId);

    /**
     * 查询已同步的类目列表
     *
     * @param datasourceId 数据源 ID
     * @param categoryType 类目类型过滤（可选）：CATEGORY_METRIC / CATEGORY_DIMENSION
     */
    List<AloudataCategoryEntity> listSyncedCategories(Long datasourceId, String categoryType);

    /**
     * 分页查询已同步的指标列表
     *
     * @param datasourceId 数据源 ID
     * @param query        分页查询参数
     * @return 分页结果
     */
    IPage<AloudataMetricSemanticDTO> pageMetrics(Long datasourceId, AloudataMetricPageQuery query);

    /**
     * 分页查询已同步的维度列表
     *
     * @param datasourceId 数据源 ID
     * @param query        分页查询参数
     * @return 分页结果
     */
    IPage<AloudataDimensionSemanticDTO> pageDimensions(Long datasourceId, AloudataDimensionPageQuery query);

    /**
     * 按指标类目分组查询指标列表
     *
     * @param datasourceId      数据源 ID
     * @param keyword           搜索关键词（可选）
     * @param categoryId        类目 ID 过滤（可选）
     * @param limitPerCategory  每个类目返回的最大条数，小于等于 0 表示不限制（可选）
     * @return 按类目分组的指标列表
     */
    List<MetricCategoryGroupDTO> listMetricsGroupByCategory(Long datasourceId, String keyword,
                                                            String categoryId, int limitPerCategory);

    /**
     * 按维度类目分组查询维度列表
     *
     * @param datasourceId      数据源 ID
     * @param keyword           搜索关键词（可选）
     * @param categoryId        类目 ID 过滤（可选）
     * @param limitPerCategory  每个类目返回的最大条数，小于等于 0 表示不限制（可选）
     * @return 按类目分组的维度列表
     */
    List<DimensionCategoryGroupDTO> listDimensionsGroupByCategory(Long datasourceId, String keyword,
                                                                  String categoryId, int limitPerCategory);

    /**
     * 查询类目下的指标/维度数量统计
     *
     * @param datasourceId 数据源 ID
     * @param categoryType 类目类型：CATEGORY_METRIC / CATEGORY_DIMENSION
     * @return 类目统计列表
     */
    List<AloudataCategoryCountDTO> listCategoryCounts(Long datasourceId, String categoryType);

    /**
     * 将已同步到 MySQL 的指标和维度数据向量化并写入 ES 索引
     * <p>
     * 不从 Aloudata API 重新拉取，仅从本地 MySQL 分页读取数据，
     * 生成向量（如 EmbeddingModel 可用）并写入 Elasticsearch。
     * 适用于：ES 索引损坏重建、EmbeddingModel 切换后重新向量化等场景。
     *
     * @param datasourceId 数据源 ID
     * @return 同步结果（metricCount/dimensionCount 为 ES 写入数量）
     */
    SyncResult rebuildEsIndex(Long datasourceId);
}
