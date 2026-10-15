package vip.mate.dataagent.dataset;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 源端不支持排序/分页时的 DataAgent 有界残余执行：
 * 对已读取的受控行集在内存中排序、切片。调用方必须保证行集有界（读取上限内），
 * 不允许对无界数据做全量内存处理。
 */
public final class ResidualRowOperations {

    private ResidualRowOperations() {
    }

    /** 按排序条件排序；null 值在升序时排最后、降序时排最前。 */
    public static void sort(List<Map<String, Object>> rows, List<DatasetSort> orders) {
        if (orders == null || orders.isEmpty()) return;
        Comparator<Object> valueComparator = Comparator.nullsLast((a, b) -> {
            try {
                return new BigDecimal(String.valueOf(a)).compareTo(new BigDecimal(String.valueOf(b)));
            } catch (NumberFormatException ignored) {
                return String.valueOf(a).compareTo(String.valueOf(b));
            }
        });
        Comparator<Map<String, Object>> comparator = null;
        for (DatasetSort order : orders) {
            Comparator<Map<String, Object>> current = Comparator.comparing(
                    row -> row.get(order.field()), valueComparator);
            if ("desc".equalsIgnoreCase(order.direction())) current = current.reversed();
            comparator = comparator == null ? current : comparator.thenComparing(current);
        }
        if (comparator != null) rows.sort(comparator);
    }

    /** 按 limit/offset 切片；任一为空时按全量返回起点处理。 */
    public static <T> List<T> paginate(List<T> rows, Integer limit, Integer offset) {
        int from = offset == null ? 0 : Math.max(offset, 0);
        if (from >= rows.size()) return List.of();
        int to = limit == null ? rows.size() : Math.min(rows.size(), from + limit);
        return rows.subList(from, to);
    }
}
