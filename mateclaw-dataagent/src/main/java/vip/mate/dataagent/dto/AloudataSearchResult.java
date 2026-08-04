package vip.mate.dataagent.dto;

import lombok.Data;

import java.util.List;

/**
 * Aloudata 语义检索结果
 * <p>
 * 包含指标级和维度级命中项，用于 aloudata_search_semantic Tool 返回。
 * 检索粒度为指标/维度，而非表级。
 */
@Data
public class AloudataSearchResult {

    /** 搜索关键词 */
    private String query;

    /** 数据源 ID */
    private Long datasourceId;

    /** 指标命中列表 */
    private List<MetricHit> metricHits;

    /** 维度命中列表 */
    private List<DimensionHit> dimensionHits;

    /** 检索耗时（毫秒） */
    private long elapsedMs;

    /**
     * 指标级命中项
     */
    @Data
    public static class MetricHit {

        /** 指标英文名（用于构造 metrics_query 请求） */
        private String metricName;

        /** 指标展示名 */
        private String metricDisplayName;

        /** 指标类型：ATOMIC/DERIVED/COMPOSITE */
        private String type;

        /** 业务口径描述 */
        private String businessCaliber;

        /** 同义词（逗号分隔） */
        private String synonyms;

        /** 类目名称 */
        private String categoryName;

        /** 单位 */
        private String unit;

        /** 可用维度名列表 */
        private List<String> availableDimensions;

        /** 匹配分数 */
        private double score;

        /** 匹配来源：keyword / semantic / hybrid */
        private String matchSource;

        /**
         * 构建 Prompt 信息（精简模式）。
         * <p>
         * 只保留构造 metrics_query 必需的核心字段：metricName、displayName、type、availableDimensions。
         * 精简输出大幅减少体积，避免检索结果超过 spill 阈值后 LLM 只看到 800 字符 preview
         * 而遗漏关键信息（消歧提示、后排指标、族级口径等）。
         * <p>
         * 预计单条约 80-150 字符，10 条指标 + 10 条维度 ≈ 2000-3000 字符，远低于 8000 字符 spill 阈值。
         */
        public String getPromptInfo() {
            StringBuilder sb = new StringBuilder();
            sb.append(metricName);
            if (metricDisplayName != null && !metricDisplayName.isBlank()) {
                sb.append("(").append(metricDisplayName).append(")");
            }
            if (type != null && !type.isBlank()) {
                sb.append(" [").append(type).append("]");
            }
            if (availableDimensions != null && !availableDimensions.isEmpty()) {
                sb.append(", 可用维度: ").append(String.join(", ", availableDimensions));
            }
            return sb.toString();
        }

        /**
         * 构建详情信息（完整模式）。
         * <p>
         * 保留所有字段，用于需要完整信息的场景（如指标详情查看、调试等）。
         */
        public String getDetailInfo() {
            StringBuilder sb = new StringBuilder();
            sb.append(metricName);
            if (metricDisplayName != null && !metricDisplayName.isBlank()) {
                sb.append("(").append(metricDisplayName).append(")");
            }
            if (type != null && !type.isBlank()) {
                sb.append(" [").append(type).append("]");
            }
            if (businessCaliber != null && !businessCaliber.isBlank()) {
                sb.append(" - ").append(businessCaliber);
            }
            if (synonyms != null && !synonyms.isBlank()) {
                sb.append(", 同义词: ").append(synonyms);
            }
            if (categoryName != null && !categoryName.isBlank()) {
                sb.append(", 类目: ").append(categoryName);
            }
            if (unit != null && !unit.isBlank()) {
                sb.append(", 单位: ").append(unit);
            }
            if (availableDimensions != null && !availableDimensions.isEmpty()) {
                sb.append(", 可用维度: ").append(String.join(", ", availableDimensions));
            }
            return sb.toString();
        }
    }

    /**
     * 维度级命中项
     */
    @Data
    public static class DimensionHit {

        /** 维度英文名（用于构造 metrics_query 请求） */
        private String dimName;

        /** 维度展示名 */
        private String dimDisplayName;

        /** 维度数据类型 */
        private String originDataType;

        /** 维度描述 */
        private String dimDescription;

        /** 同义词（逗号分隔） */
        private String synonyms;

        /** 是否时间维度 */
        private boolean timeDimension;

        /** 示例值 */
        private String exampleValues;

        /** 匹配分数 */
        private double score;

        /** 匹配来源：keyword / semantic / hybrid */
        private String matchSource;

        /**
         * 构建 Prompt 信息（精简模式）。
         * <p>
         * 只保留构造 metrics_query 必需的核心字段：dimName、displayName、dataType、timeDimension。
         * 精简输出大幅减少体积，避免检索结果超过 spill 阈值后 LLM 只看到 800 字符 preview
         * 而遗漏关键信息。
         */
        public String getPromptInfo() {
            StringBuilder sb = new StringBuilder();
            sb.append(dimName);
            if (dimDisplayName != null && !dimDisplayName.isBlank()) {
                sb.append("(").append(dimDisplayName).append(")");
            }
            if (originDataType != null && !originDataType.isBlank()) {
                sb.append(" [").append(originDataType).append("]");
            }
            if (timeDimension) {
                sb.append(", 时间维度");
            }
            return sb.toString();
        }

        /**
         * 构建详情信息（完整模式）。
         * <p>
         * 保留所有字段，用于需要完整信息的场景。
         */
        public String getDetailInfo() {
            StringBuilder sb = new StringBuilder();
            sb.append(dimName);
            if (dimDisplayName != null && !dimDisplayName.isBlank()) {
                sb.append("(").append(dimDisplayName).append(")");
            }
            if (originDataType != null && !originDataType.isBlank()) {
                sb.append(" [").append(originDataType).append("]");
            }
            if (dimDescription != null && !dimDescription.isBlank()) {
                sb.append(" - ").append(dimDescription);
            }
            if (synonyms != null && !synonyms.isBlank()) {
                sb.append(", 同义词: ").append(synonyms);
            }
            if (timeDimension) {
                sb.append(", 时间维度");
            }
            if (exampleValues != null && !exampleValues.isBlank()) {
                sb.append(", 示例: ").append(exampleValues);
            }
            return sb.toString();
        }
    }
}
