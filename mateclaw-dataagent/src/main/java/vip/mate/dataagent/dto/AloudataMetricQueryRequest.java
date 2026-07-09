package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Aloudata 指标查询请求
 * <p>
 * 字段对应 Aloudata 指标数据查询 API（/semantic/api/v1.1/metrics/query）的 Body 参数：
 * <ul>
 *   <li>filters: 全局筛选，表达式字符串数组，对全部指标做维度过滤</li>
 *   <li>timeConstraint: 指标日期范围约束，表达式字符串</li>
 * </ul>
 */
@Data
public class AloudataMetricQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 指标名称列表 */
    private List<String> metrics;

    /** 维度名称列表 */
    private List<String> dimensions;

    /**
     * 全局筛选条件（表达式字符串数组）
     * <p>
     * 符合 Aloudata API 5.4 节 filters 参数规范，每个元素为维度过滤表达式，例如：
     * <ul>
     *   <li>文本维度：[province] IN ("浙江省","江苏省")</li>
     *   <li>数值维度：[product_id] IN (13,18)</li>
     *   <li>日期维度：[metric_time__day] BETWEEN ("2024-01-01","2024-01-31")</li>
     * </ul>
     */
    private List<String> filters;

    /**
     * 指标日期范围约束（表达式字符串）
     * <p>
     * 符合 Aloudata API 5.6 节 timeConstraint 参数规范，例如：
     * ([metric_time__month]= DateTrunc(Today(),"MONTH"))
     */
    private String timeConstraint;

    /** 排序条件 */
    private List<Map<String, Object>> orderBy;

    /** 限制返回行数 */
    private Integer limit;

    /** 偏移量 */
    private Integer offset;
}
