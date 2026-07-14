package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 归因分析请求 DTO
 * <p>
 * 基于仪表盘组件的指标和维度信息，请求 Aloudata 归因分析 API，
 * 获取指标变动归因结果。
 */
@Data
public class AttributionAnalysisRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 数据源 ID */
    private Long datasourceId;

    /** 指标名称 */
    private String metric;

    /** 分析维度列表 */
    private List<String> dimensions;

    /** 时间粒度：DAY / WEEK / MONTH / QUARTER / YEAR */
    private String granularity;

    /** 对比类型：CUSTOM / DOD / YOY / MOM / QOQ / WOW */
    private String comparisonType;

    /** 当前时间表达式，如 DateTrunc([metric_time],"DAY")="2025-07-07" */
    private String currentTimeExpr;

    /** 对比时间表达式（时间对比时使用） */
    private String compareTimeExpr;

    /** 自定义对比开始时间（comparisonType=CUSTOM 时使用） */
    private String startDateTime;

    /** 自定义对比结束时间（comparisonType=CUSTOM 时使用） */
    private String endDateTime;

    /** 筛选条件表达式列表 */
    private List<String> filters;

    /** 下钻维度（归因下钻时使用，单维度） */
    private String drillDimension;

    /** 下钻维度值筛选（归因下钻时追加到 filters） */
    private List<String> drillFilters;
}
