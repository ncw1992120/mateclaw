package vip.mate.dataagent.dto;

import java.util.List;
import java.util.Map;

/** Aloudata 指标视图结果的内部标准化表示。 */
public record AloudataAnalysisViewResult(
        List<Map<String, Object>> columns,
        List<Map<String, Object>> rows,
        long total,
        String queryId) {
    public AloudataAnalysisViewResult {
        columns = columns == null ? List.of() : List.copyOf(columns);
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
