package vip.mate.dataagent.dataset;

import vip.mate.dataagent.dto.AloudataMetricQueryResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Converts the columnar response used by Aloudata metrics/query to the row shape consumed by previews. */
public final class AloudataMetricRows {
    private AloudataMetricRows() {}

    public static List<Map<String, Object>> from(AloudataMetricQueryResponse response) {
        if (response == null || response.getData() == null) return List.of();
        List<Map<String, Object>> rows = response.getData().getRows();
        if (rows != null && !rows.isEmpty()) return rows;

        Map<String, List<AloudataMetricQueryResponse.ColumnValue>> columns = response.getData().getColumns();
        if (columns == null || columns.isEmpty()) return rows == null ? List.of() : rows;
        int rowCount = columns.values().stream().filter(Objects::nonNull).mapToInt(List::size).max().orElse(0);
        List<Map<String, Object>> converted = new ArrayList<>(rowCount);
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (Map.Entry<String, List<AloudataMetricQueryResponse.ColumnValue>> column : columns.entrySet()) {
                List<AloudataMetricQueryResponse.ColumnValue> values = column.getValue();
                AloudataMetricQueryResponse.ColumnValue value = values != null && rowIndex < values.size()
                        ? values.get(rowIndex) : null;
                row.put(column.getKey(), value == null ? null : value.getValue());
            }
            converted.add(row);
        }
        return converted;
    }
}
