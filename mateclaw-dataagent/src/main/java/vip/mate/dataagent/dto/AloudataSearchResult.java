package vip.mate.dataagent.dto;

import lombok.Data;
import vip.mate.dataagent.constants.DataAgentConstants;

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

        /**
         * 与用户查询相关的维度简要列表（按相关度降序，最多 ALOUDATA_SEARCH_RELEVANT_DIM_TOP_N 个）。
         * <p>
         * null 表示尚未经过 enrich（族级兜底补入项的待补标记）；
         * 空列表表示已 enrich 但无与查询直接相关的维度。
         */
        private List<DimensionBrief> relevantDimensions;

        /** 匹配分数 */
        private double score;

        /** 匹配来源：keyword / semantic / hybrid */
        private String matchSource;

        /**
         * 维度简要信息（英文名 + 展示名），用于 prompt 紧凑渲染
         */
        public record DimensionBrief(String dimName, String dimDisplayName) {
        }

        /**
         * 渲染维度段（MetricHit 各 prompt 出口的共用实现）。
         * <ul>
         *   <li>availableDimensions 为 null/空 → 返回空串（调用方不追加）</li>
         *   <li>relevantDimensions 为空但共 N&gt;0 个 →
         *       「维度共N个（未匹配到与查询直接相关的维度；完整列表调用 aloudata_metric_available_dimensions）」</li>
         *   <li>relevantDimensions 非空 → 「维度共N个, 相关维度: name(展示名), ...」；
         *       相关维度数 &lt; 总数时追加工具引导</li>
         * </ul>
         */
        public String getDimensionsPrompt() {
            List<String> all = availableDimensions;
            if (all == null || all.isEmpty()) {
                return "";
            }
            String toolName = DataAgentConstants.ALOUDATA_TOOL_METRIC_AVAILABLE_DIMENSIONS;
            StringBuilder sb = new StringBuilder("维度共").append(all.size()).append("个");
            List<DimensionBrief> relevant = relevantDimensions;
            if (relevant == null || relevant.isEmpty()) {
                sb.append("（未匹配到与查询直接相关的维度；完整列表调用 ").append(toolName).append("）");
                return sb.toString();
            }
            sb.append(", 相关维度: ");
            for (int i = 0; i < relevant.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                DimensionBrief brief = relevant.get(i);
                sb.append(brief.dimName());
                if (brief.dimDisplayName() != null && !brief.dimDisplayName().isBlank()) {
                    sb.append("(").append(brief.dimDisplayName()).append(")");
                }
            }
            if (relevant.size() < all.size()) {
                sb.append("；完整列表调用 ").append(toolName);
            }
            return sb.toString();
        }

        /**
         * 构建 Prompt 信息（精简模式）。
         * <p>
         * 只保留构造 metrics_query 必需的核心字段：metricName、displayName、type、relevantDimensions。
         * 维度段由 {@link #getDimensionsPrompt()} 统一渲染（总数 + 查询相关 TopN + 工具引导），
         * 不再罗列全量维度英文名，避免检索结果超长触发 spill 后 LLM 只看到 preview。
         * <p>
         * 预计单条约 80-200 字符，10 条指标 + 10 条维度 ≈ 3000 字符以内，远低于 8000 字符 spill 阈值。
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
            String dimsPrompt = getDimensionsPrompt();
            if (!dimsPrompt.isEmpty()) {
                sb.append(", ").append(dimsPrompt);
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
                String dimsPrompt = getDimensionsPrompt();
                if (!dimsPrompt.isEmpty()) {
                    sb.append(", ").append(dimsPrompt);
                }
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
