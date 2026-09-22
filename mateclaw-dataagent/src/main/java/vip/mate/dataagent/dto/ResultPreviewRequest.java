package vip.mate.dataagent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * 结果集分页预览请求（实施计划「统一接口契约 §4」结果预览行）：
 * 请求只携带 sort/pagination/parameters?/requestId，不是完整 QueryContext —— 排序和分页
 * 由服务端对完整结果执行，parameters 保留给结果级筛选扩展。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResultPreviewRequest(QueryContextDTO.SortSpec sort, QueryContextDTO.PaginationSpec pagination,
                                   Map<String, Object> parameters, String requestId) {
    public ResultPreviewRequest {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
