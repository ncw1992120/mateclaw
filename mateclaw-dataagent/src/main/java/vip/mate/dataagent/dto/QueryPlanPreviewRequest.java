package vip.mate.dataagent.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 查询计划预览请求（编辑器验证单个数据集计划，不落库）。
 *
 * @param datasetId    已存在的真实数据集 ID
 * @param inputName    输入别名；为空时使用默认值
 * @param queryContext 页面运行时查询上下文
 * @param queryConfig  尚未保存时的受校验查询配置草稿；为空时按无配置数据集处理
 */
public record QueryPlanPreviewRequest(long datasetId, String inputName, QueryContextDTO queryContext, JsonNode queryConfig) {
    public QueryPlanPreviewRequest {
        if (datasetId <= 0) {
            throw new IllegalArgumentException("datasetId is required");
        }
        if (inputName == null || inputName.isBlank()) {
            inputName = "dataset";
        }
    }
}
