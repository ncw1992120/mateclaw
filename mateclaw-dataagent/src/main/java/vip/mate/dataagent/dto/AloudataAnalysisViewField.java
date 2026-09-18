package vip.mate.dataagent.dto;

/**
 * Aloudata 指标视图字段（指标或维度）的名称 / 展示名 / 描述。
 * <p>
 * 指标视图详情（{@code analysisview/queryByName}）只返回 metrics/dimensions 的名称数组，
 * 展示名与描述需分别从 {@code metrics/batchDetail}（metricDisplayName / businessCaliber）
 * 与 {@code dimension/list}（dimDisplayName / dimDescription）补齐。
 *
 * @param name        字段名（指标 metricName / 维度 dimName）
 * @param displayName 展示名
 * @param description 字段描述（指标取业务口径 businessCaliber，维度取 dimDescription）
 * @param role        字段角色：{@code measure}（指标）或 {@code dimension}（维度）
 */
public record AloudataAnalysisViewField(
        String name,
        String displayName,
        String description,
        String role) {

    public static final String ROLE_MEASURE = "measure";
    public static final String ROLE_DIMENSION = "dimension";
}
