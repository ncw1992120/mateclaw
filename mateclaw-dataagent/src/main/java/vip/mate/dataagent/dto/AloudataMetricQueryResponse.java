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

    /** 详细错误信息（部分 Aloudata 错误只填充此字段） */
    private String detailErrorMsg;

    /** 指标查询 API 标准错误详情字段 */
    private String message;

    /** 响应数据 */
    private MetricData data;

    /** 追踪 ID */
    private String traceId;

    /** 指标查询业务响应是否失败；兼容旧响应只返回 code 的情况。 */
    public boolean hasBusinessFailure() {
        return Boolean.FALSE.equals(success)
                || (code != null && !"200".equals(code) && !"0".equals(code));
    }

    /** 统一提取失败详情，兼容 Aloudata API 的 message 及历史错误字段。 */
    public String failureDescription() {
        String detail = firstNonBlank(detailErrorMsg, errorMsg, message);
        StringBuilder message = new StringBuilder("Aloudata 指标查询失败: ")
                .append(detail == null || detail.isBlank() ? "上游未提供错误详情" : detail);
        if (code != null && !code.isBlank()) message.append(" (code=").append(code).append(')');
        if (traceId != null && !traceId.isBlank()) message.append(" (traceId=").append(traceId).append(')');
        return message.toString();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

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
