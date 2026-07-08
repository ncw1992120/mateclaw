package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Aloudata 指标查询响应
 */
@Data
public class AloudataMetricQueryResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否成功 */
    private Boolean success;

    /** 响应码 */
    private String code;

    /** 错误信息 */
    private String errorMsg;

    /** 响应数据 */
    private MetricData data;

    /** 追踪 ID */
    private String traceId;

    /**
     * 指标数据
     */
    @Data
    public static class MetricData implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 列式数据：key=列名，value=该列的值列表 */
        Map<String, List<ColumnValue>> columns;

        /** 行式数据（部分查询可能为 null，需从 columns 转换） */
        List<Map<String, Object>> rows;

        /** 总行数 */
        Long total;
    }

    /**
     * 列值单元
     */
    @Data
    public static class ColumnValue implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 值 */
        private Object value;

        /** 标记 */
        private Object flag;

        /** 计数 */
        private Integer count;
    }
}
