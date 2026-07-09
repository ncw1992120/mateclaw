package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 仪表盘运行时筛选上下文 DTO
 * <p>
 * 前端预览仪表盘时传入的动态筛选条件，包含时间范围和维度筛选值。
 * 后端将这些条件合并到每个组件的静态 filters 中，实现筛选联动取数。
 */
@Data
public class DashboardFilterContextDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 时间范围筛选 */
    private TimeRangeValue timeRange;

    /** 维度筛选值列表 */
    private List<FilterValue> dimensionFilters;

    /** 触发此次筛选的筛选器组件 ID（用于区分全局/组件绑定筛选） */
    private String sourceFilterId;

    /**
     * 时间范围筛选值
     */
    @Data
    public static class TimeRangeValue implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 预设类型：today / 7d / 30d / 90d / custom */
        private String preset;

        /** 自定义起始日期（preset=custom 时使用，格式 yyyy-MM-dd） */
        private String start;

        /** 自定义结束日期（preset=custom 时使用，格式 yyyy-MM-dd） */
        private String end;
    }

    /**
     * 维度筛选值
     */
    @Data
    public static class FilterValue implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 筛选字段名（对应维度名） */
        private String field;

        /** 筛选值（单个值或数组） */
        private Object value;
    }
}
