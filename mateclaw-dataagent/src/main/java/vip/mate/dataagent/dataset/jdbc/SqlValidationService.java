package vip.mate.dataagent.dataset.jdbc;

import vip.mate.dataagent.dataset.DatasetFilter;

import java.util.List;

public interface SqlValidationService {
    CompiledJdbcQuery compile(String baseSql, List<String> columns, List<DatasetFilter> filters,
                              int limit, int offset);
}
