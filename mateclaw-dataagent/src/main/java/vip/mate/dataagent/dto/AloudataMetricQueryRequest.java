package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Aloudata 指标查询请求
 */
@Data
public class AloudataMetricQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 指标名称列表 */
    private List<String> metrics;

    /** 维度名称列表 */
    private List<String> dimensions;

    /** 过滤条件 */
    private List<Map<String, Object>> filters;

    /** 排序条件 */
    private List<Map<String, Object>> orderBy;

    /** 限制返回行数 */
    private Integer limit;

    /** 偏移量 */
    private Integer offset;
}
