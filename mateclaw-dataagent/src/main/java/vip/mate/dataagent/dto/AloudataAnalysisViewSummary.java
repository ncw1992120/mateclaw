package vip.mate.dataagent.dto;

/** Aloudata 指标视图目录项。 */
public record AloudataAnalysisViewSummary(
        String id,
        String viewName,
        String displayName,
        String categoryId,
        String categoryName) {
}
