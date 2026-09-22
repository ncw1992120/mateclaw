package vip.mate.dataagent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 单数据集查询计划（实施计划「统一接口契约 §2」）。
 * <p>
 * {@code pushdown} 是服务端 Planner 的计算结果，不允许前端伪造；
 * {@code residualOperations} 描述未下推、由结果集服务或后续阶段完成的有界操作；
 * 字段名一律是数据源技术字段名。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record DatasetQueryPlanDTO(
        String datasetId,
        String inputName,
        List<String> columns,
        List<FilterSpec> filters,
        List<OrderSpec> orders,
        QueryContextDTO.PaginationSpec pagination,
        PushdownSpec pushdown,
        List<String> residualOperations,
        /** 显式空集合短路：不访问数据源，直接返回空结果。 */
        boolean shortCircuitEmpty,
        /** 有 Python 脚本（可能改变行数/粒度）时源端读取的行数上限，保证结果集阶段有完整数据可用。 */
        Integer readLimit) {

    public DatasetQueryPlanDTO {
        columns = columns == null ? List.of() : List.copyOf(columns);
        filters = filters == null ? List.of() : List.copyOf(filters);
        orders = orders == null ? List.of() : List.copyOf(orders);
        residualOperations = residualOperations == null ? List.of() : List.copyOf(residualOperations);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FilterSpec(String field, String operator, Object value) {
        public FilterSpec {
            if (field == null || field.isBlank()) {
                throw new IllegalArgumentException("plan filter field must not be blank");
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderSpec(String field, String direction) {
        public OrderSpec {
            if (field == null || field.isBlank()) {
                throw new IllegalArgumentException("plan order field must not be blank");
            }
            if (direction == null || direction.isBlank()) {
                direction = "asc";
            }
            direction = direction.toLowerCase(java.util.Locale.ROOT);
            if (!direction.equals("asc") && !direction.equals("desc")) {
                throw new IllegalArgumentException("plan order direction must be asc or desc");
            }
        }
    }

    /** 服务端计算的下推报告；某项为 false 时必须有对应 residual 操作或明确失败。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PushdownSpec(boolean filters, boolean sort, boolean pagination) {
    }
}
