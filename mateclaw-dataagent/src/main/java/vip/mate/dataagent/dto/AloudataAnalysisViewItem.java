package vip.mate.dataagent.dto;

/**
 * Aloudata 指标视图平铺列表项。
 * <p>
 * 来源 {@code analysis_view_list}（/{@code anymetrics/api/v1/analysisview/list}），
 * 相比树状目录额外携带 {@code basicAttributes.owner}，用于识别「我的视图」
 * （{@code owner} 等于当前数据源认证值）并支持「只看我的」筛选。
 *
 * @param id          视图 ID
 * @param viewName    视图名称（查询取数时的 viewName）
 * @param displayName 视图展示名
 * @param description 视图描述
 * @param owner       视图创建者（Aloudata UID）
 * @param mine        是否归属当前认证账号
 */
public record AloudataAnalysisViewItem(
        String id,
        String viewName,
        String displayName,
        String description,
        String owner,
        boolean mine) {
}
