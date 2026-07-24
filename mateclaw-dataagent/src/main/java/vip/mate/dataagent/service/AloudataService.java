package vip.mate.dataagent.service;

import vip.mate.dataagent.dto.*;

import java.util.List;
import java.util.Map;

/**
 * Aloudata 指标平台服务接口
 * <p>
 * 提供 Aloudata CAN 指标平台的连接管理、指标查询、维度查询、语义同步、归因分析等功能。
 */
public interface AloudataService {

    /**
     * 测试 Aloudata 连接
     *
     * @param config Aloudata 配置信息
     * @return 是否连接成功
     */
    boolean testConnection(AloudataConfigDTO config);

    /**
     * 查询指标列表
     *
     * @param datasourceId 数据源 ID
     * @return 指标列表
     */
    List<AloudataMetricVO> listMetrics(Long datasourceId);

    /**
     * 查询维度列表
     *
     * @param datasourceId 数据源 ID
     * @return 维度列表
     */
    List<AloudataDimensionVO> listDimensions(Long datasourceId);

    /**
     * 执行指标数据查询
     *
     * @param datasourceId 数据源 ID
     * @param request      查询请求
     * @return 查询结果
     */
    AloudataMetricQueryResponse queryMetrics(Long datasourceId, AloudataMetricQueryRequest request);

    /**
     * 查询维度可选值列表
     * <p>
     * 通过 Aloudata dimension_values 端点获取指定维度的可选值列表，用于筛选器动态加载选项。
     *
     * @param datasourceId 数据源 ID
     * @param dimName      维度英文名
     * @param keyword      搜索关键字（匹配维度值），可为 null
     * @param limit        最大返回条数
     * @return 维度值列表
     */
    List<String> queryDimensionValues(Long datasourceId, String dimName, String keyword, int limit);

    /**
     * 查询指标语义信息列表（含同义词、业务口径、可用维度）
     * <p>
     * 用于同步指标平台的语义定义到本地语义模型，避免用户手动填写。
     *
     * @param datasourceId 数据源 ID
     * @return 指标语义信息列表
     */
    List<AloudataMetricSemanticDTO> listMetricSemantics(Long datasourceId);

    /**
     * 查询维度语义信息列表（含同义词、描述、数据类型）
     * <p>
     * 用于同步指标平台的维度定义到本地语义模型，避免用户手动填写。
     *
     * @param datasourceId 数据源 ID
     * @return 维度语义信息列表
     */
    List<AloudataDimensionSemanticDTO> listDimensionSemantics(Long datasourceId);

    /**
     * 校验指标是否支持归因分析
     *
     * @param datasourceId 数据源 ID
     * @param metric       指标名称
     * @return 校验结果
     */
    AttributionAnalysisResponse.CheckResult checkAttribution(Long datasourceId, String metric);

    /**
     * 多维归因分析查询
     * <p>
     * 针对指标以及维度进行多维归因结果的查询，返回不同对比日期的变化情况以及各个维度的贡献率。
     *
     * @param request 归因分析请求
     * @return 多维归因结果
     */
    AttributionAnalysisResponse.MultiDimResult queryMultiDimAttribution(AttributionAnalysisRequest request);

    /**
     * 多维归因下钻查询
     * <p>
     * 对特定业务指标的变动原因进行细致分析，按单一维度下钻。
     *
     * @param request 归因分析请求（drillDimension 为下钻维度）
     * @return 下钻归因结果
     */
    AttributionAnalysisResponse.MultiDimResult queryDrilldownAttribution(AttributionAnalysisRequest request);

    /**
     * 指标拆解
     * <p>
     * 对一个指标进行拆解，自动生成指标树结构，通常用于后续指标树归因。
     *
     * @param datasourceId 数据源 ID
     * @param metric       指标名称
     * @return 指标树定义
     */
    AttributionAnalysisResponse.MetricTreeDef breakdownMetric(Long datasourceId, String metric);

    /**
     * 指标树归因分析（时间对比）
     * <p>
     * 基于指标拆解返回的 metricTreeDef，分析同一指标树在两个时间范围内的变化。
     *
     * @param datasourceId     数据源 ID
     * @param metricTreeDef    指标树定义（通常由 breakdownMetric 返回）
     * @param currentTimeExpr  当前时间表达式
     * @param compareTimeExpr  对比时间表达式
     * @param filters          全局过滤条件
     * @return 各节点的归因结果，key 为节点 ID
     */
    Map<String, AttributionAnalysisResponse.TreeNodeAttribution> queryTreeAttribution(
            Long datasourceId,
            AttributionAnalysisResponse.MetricTreeDef metricTreeDef,
            String currentTimeExpr,
            String compareTimeExpr,
            List<String> filters);
}
