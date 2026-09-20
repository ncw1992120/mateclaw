package vip.mate.dataagent.dataset.jdbc;

import vip.mate.dataagent.dataset.DatasetFilter;

import java.util.List;
import java.util.Map;

public interface SqlValidationService {
    CompiledJdbcQuery compile(String baseSql, List<String> columns, List<DatasetFilter> filters,
                              int limit, int offset);

    /**
     * 带命名参数绑定的编译：把 baseSql 里的 {@code :name} 占位符绑成 JDBC 参数。
     * <p>
     * 参数顺序必须是「baseSql 内的 ? → filters 的 ? → limit/offset」，因此实现里要先替换
     * baseSql 的占位符并收集值，再追加 filters 与分页参数，否则 PreparedStatement 设值会错位。
     * <p>
     * 默认实现忽略参数（保持既有调用方行为不变）；支持绑定的实现应覆写本方法。
     *
     * @param parameters 命名参数值，缺失的占位符由实现决定是报错还是留空
     */
    default CompiledJdbcQuery compile(String baseSql, List<String> columns, List<DatasetFilter> filters,
                                      int limit, int offset, Map<String, Object> parameters) {
        return compile(baseSql, columns, filters, limit, offset);
    }
}
