package vip.mate.dataagent.dataset;

import java.util.List;

/** Adapter 对过滤、投影和分页实际下推情况的审计结果。 */
public record PushdownReport(
        List<DatasetFilter> pushedFilters,
        List<DatasetFilter> residualFilters,
        boolean projectionPushed,
        boolean limitPushed,
        String sourceQueryDigest) {
    public PushdownReport {
        pushedFilters = pushedFilters == null ? List.of() : List.copyOf(pushedFilters);
        residualFilters = residualFilters == null ? List.of() : List.copyOf(residualFilters);
    }
}
