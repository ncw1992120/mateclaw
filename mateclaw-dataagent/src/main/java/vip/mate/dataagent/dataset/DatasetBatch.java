package vip.mate.dataagent.dataset;

import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 一批结果：小结果可内联，大结果必须通过 ObjectRef 传递。 */
public record DatasetBatch(
        List<Map<String, Object>> rows,
        ObjectRef objectRef,
        long rowCount,
        boolean last,
        PushdownReport pushdownReport,
        Long totalCount) {
    public DatasetBatch(List<Map<String, Object>> rows, ObjectRef objectRef, long rowCount, boolean last) {
        this(rows, objectRef, rowCount, last, null, null);
    }

    public DatasetBatch(List<Map<String, Object>> rows, ObjectRef objectRef, long rowCount, boolean last,
                        PushdownReport pushdownReport) {
        this(rows, objectRef, rowCount, last, pushdownReport, null);
    }

    public DatasetBatch {
        if (rows != null && objectRef != null) {
            throw new IllegalArgumentException("rows and objectRef are mutually exclusive");
        }
        if (rows == null && objectRef == null) {
            throw new IllegalArgumentException("rows or objectRef is required");
        }
        if (rowCount < 0) {
            throw new IllegalArgumentException("rowCount must not be negative");
        }
        rows = rows == null ? null : rows.stream()
                .map(row -> Collections.unmodifiableMap(new LinkedHashMap<>(row)))
                .toList();
    }
}
