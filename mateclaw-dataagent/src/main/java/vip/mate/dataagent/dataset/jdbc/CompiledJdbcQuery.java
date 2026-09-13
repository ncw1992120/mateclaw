package vip.mate.dataagent.dataset.jdbc;

import java.util.List;

/** 已通过校验、可交给 PreparedStatement 执行的 JDBC 查询。 */
public record CompiledJdbcQuery(String sql, List<Object> parameters, String digest) {
    public CompiledJdbcQuery {
        if (sql == null || sql.isBlank() || digest == null || digest.isBlank()) {
            throw new IllegalArgumentException("compiled query is incomplete");
        }
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }
}
