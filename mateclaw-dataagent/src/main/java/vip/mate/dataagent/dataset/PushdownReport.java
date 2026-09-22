package vip.mate.dataagent.dataset;

import java.util.List;

/**
 * Adapter 对过滤、投影、排序和分页实际下推情况的审计结果。
 * <p>
 * {@code ordersPushed} 列出实际下推到源端的排序（不支持时为空且必须由 DataAgent 有界残余执行）；
 * {@code totalCountRequested} 表示是否真正向源端请求了精确总数——不支持总数的源端必须为 false，
 * 不允许返回虚假总数。
 */
public record PushdownReport(
        List<DatasetFilter> pushedFilters,
        List<DatasetFilter> residualFilters,
        List<DatasetSort> ordersPushed,
        boolean projectionPushed,
        boolean limitPushed,
        boolean totalCountRequested,
        String sourceQueryDigest) {

    /** 旧构造重载：无排序、未请求总数，保持既有调用方行为不变。 */
    public PushdownReport(List<DatasetFilter> pushedFilters, List<DatasetFilter> residualFilters,
                          boolean projectionPushed, boolean limitPushed, String sourceQueryDigest) {
        this(pushedFilters, residualFilters, List.of(), projectionPushed, limitPushed, false, sourceQueryDigest);
    }

    public PushdownReport {
        pushedFilters = pushedFilters == null ? List.of() : List.copyOf(pushedFilters);
        residualFilters = residualFilters == null ? List.of() : List.copyOf(residualFilters);
        ordersPushed = ordersPushed == null ? List.of() : List.copyOf(ordersPushed);
    }
}
