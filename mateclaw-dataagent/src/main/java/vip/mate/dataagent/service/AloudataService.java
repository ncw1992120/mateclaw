package vip.mate.dataagent.service;

import vip.mate.dataagent.dto.*;

import java.util.List;

/**
 * Aloudata 指标平台服务接口
 * <p>
 * 提供 Aloudata CAN 指标平台的连接管理、指标查询、维度查询、语义同步等功能。
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
     * 执行指标数据查询
     *
     * @param datasourceId 数据源 ID
     * @param request      查询请求
     * @return 查询结果
     */
    AloudataMetricQueryResponse queryMetrics(Long datasourceId, AloudataMetricQueryRequest request);

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
}
