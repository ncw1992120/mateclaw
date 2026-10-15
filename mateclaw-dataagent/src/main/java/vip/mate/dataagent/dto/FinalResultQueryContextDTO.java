package vip.mate.dataagent.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/** Python 最终结果集查询的实际运行参数。 */
public record FinalResultQueryContextDTO(
        Map<String, Object> parameters,
        QueryContextDTO.SortSpec sort,
        QueryContextDTO.PaginationSpec pagination) {

    public FinalResultQueryContextDTO {
        parameters = parameters == null ? Map.of()
                : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }
}
