package vip.mate.dataagent.dto;

import lombok.Data;

import java.util.List;

/**
 * 业务术语语义检索结果
 */
@Data
public class BusinessTermSearchResult {

    /** 搜索关键词 */
    private String query;

    /** 租户编码 */
    private String tenantCode;

    /** 术语命中列表 */
    private List<TermHit> termHits;

    /** 检索耗时（毫秒） */
    private long elapsedMs;

    /**
     * 术语命中项
     */
    @Data
    public static class TermHit {

        /** 术语名称 */
        private String termName;

        /** 同义词 */
        private String synonyms;

        /** 术语定义 */
        private String description;

        /** 计算公式 */
        private String calculationFormula;

        /** 数据口径 */
        private String dataCaliber;

        /** 业务规则 */
        private String businessRule;

        /** 分类 */
        private String category;

        /** 父术语名称 */
        private String parentTermName;

        /** 关联指标名称列表（metricName） */
        private List<String> relatedMetricNames;

        /** 关联维度名称列表（dimName） */
        private List<String> relatedDimensionNames;

        /** 匹配得分 */
        private double score;

        /** 匹配来源：keyword / semantic / hybrid */
        private String matchSource;
    }
}
