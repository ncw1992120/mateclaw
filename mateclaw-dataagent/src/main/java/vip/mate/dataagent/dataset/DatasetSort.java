package vip.mate.dataagent.dataset;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Locale;

/**
 * 统一排序条件：{@code field} 是数据源字段名（技术主键），direction 仅允许 asc/desc。
 * 由 QueryPlanner 校验字段白名单后传入 Adapter，Adapter 负责转换为源端语法。
 */
public record DatasetSort(String field, String direction) {
    public DatasetSort {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("sort field must not be blank");
        }
        if (direction == null || direction.isBlank()) {
            direction = "asc";
        }
        direction = direction.toLowerCase(Locale.ROOT);
        if (!direction.equals("asc") && !direction.equals("desc")) {
            throw new IllegalArgumentException("sort direction must be asc or desc");
        }
    }

    @JsonCreator
    public static DatasetSort of(@JsonProperty("field") String field, @JsonProperty("direction") String direction) {
        return new DatasetSort(field, direction);
    }
}
