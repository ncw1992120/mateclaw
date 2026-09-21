package vip.mate.dataagent.service.code;

import org.springframework.stereotype.Service;
import vip.mate.dataagent.dataset.DatasetFilter;

import java.util.Collection;
import java.util.List;

/**
 * 脚本数据集读取策略：在调用 Adapter 前拦截超限读取请求。
 *
 * 限制项（与 Runner SDK 侧校验一致，双侧防御）：
 * - 单次读取最多 {@value #MAX_FILTERS} 条筛选条件；
 * - 单个 in/not_in 条件最多 {@value #MAX_IN_VALUES} 个值（跨数据集 A→B 场景防全表拉取）；
 * - 请求负载最大 {@value #MAX_REQUEST_BYTES} KiB。
 * 错误信息只包含限制项与实际数量，不回显筛选值内容。
 */
@Service
public class ScriptDatasetReadPolicy {

    public static final int MAX_FILTERS = 50;
    public static final int MAX_IN_VALUES = 1000;
    public static final int MAX_REQUEST_BYTES = 256 * 1024;

    public void validate(List<DatasetFilter> filters, long requestBytes) {
        if (requestBytes > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException(
                    "script dataset read payload too large: " + requestBytes + " bytes exceeds " + MAX_REQUEST_BYTES);
        }
        if (filters == null) return;
        if (filters.size() > MAX_FILTERS) {
            throw new IllegalArgumentException(
                    "too many script dataset filters: " + filters.size() + " exceeds " + MAX_FILTERS);
        }
        for (DatasetFilter filter : filters) {
            if (filter == null) throw new IllegalArgumentException("script dataset filter must not be null");
            String operator = filter.operator() == null ? "" : filter.operator().toLowerCase(java.util.Locale.ROOT);
            if ("in".equals(operator) || "not_in".equals(operator)) {
                validateInValues(filter.value());
            }
        }
    }

    private void validateInValues(Object value) {
        if (!(value instanceof Collection<?> values)) {
            throw new IllegalArgumentException("script dataset in/not_in filter requires a list value");
        }
        if (values.size() > MAX_IN_VALUES) {
            throw new IllegalArgumentException(
                    "filter value count exceeds " + MAX_IN_VALUES + ": " + values.size());
        }
    }
}
