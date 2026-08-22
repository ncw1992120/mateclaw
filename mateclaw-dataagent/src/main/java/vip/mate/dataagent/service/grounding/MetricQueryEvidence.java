package vip.mate.dataagent.service.grounding;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 单次成功 {@code aloudata_metrics_query} 的「证据快照」（P0-1）。
 * <p>
 * 在工具执行时从 RAW 查询结果（spill/截断之前）抽取，供最终答案做数字对齐校验：
 * {@code numberTokens} 为该次查询结果中出现过的全部数值（规范化后），
 * {@code metricNames} 为该次查询实际执行的指标英文名，{@code dimensionNames} 为实际维度。
 * <p>
 * 生命周期：会话内当轮有效，由最终答案校验消费后随 scope context 清理。
 */
public final class MetricQueryEvidence implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 数据源 ID */
    private final Long datasourceId;

    /** 实际执行的指标英文名列表 */
    private final List<String> metricNames;

    /** 实际执行的维度英文名列表 */
    private final List<String> dimensionNames;

    /** 结果集中出现的全部数值（规范化后的规范形式集合，含 total） */
    private final Set<String> numberTokens;

    /** 结果行数（0 表示空结果） */
    private final int rowCount;

    public MetricQueryEvidence(Long datasourceId, List<String> metricNames,
                               List<String> dimensionNames, Set<String> numberTokens, int rowCount) {
        this.datasourceId = datasourceId;
        this.metricNames = metricNames == null ? List.of() : List.copyOf(metricNames);
        this.dimensionNames = dimensionNames == null ? List.of() : List.copyOf(dimensionNames);
        this.numberTokens = numberTokens == null
                ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(numberTokens));
        this.rowCount = rowCount;
    }

    public static Builder builder(Long datasourceId) {
        return new Builder(datasourceId);
    }

    /** 是否存在可校验的数值证据 */
    public boolean hasNumbers() {
        return !numberTokens.isEmpty();
    }

    public Long getDatasourceId() {
        return datasourceId;
    }

    public List<String> getMetricNames() {
        return metricNames;
    }

    public List<String> getDimensionNames() {
        return dimensionNames;
    }

    public Set<String> getNumberTokens() {
        return numberTokens;
    }

    public int getRowCount() {
        return rowCount;
    }

    /** 建造器 */
    public static final class Builder {
        private final Long datasourceId;
        private final Set<String> metricNames = new LinkedHashSet<>();
        private final Set<String> dimensionNames = new LinkedHashSet<>();
        private final Set<String> numberTokens = new LinkedHashSet<>();
        private int rowCount = 0;

        private Builder(Long datasourceId) {
            this.datasourceId = datasourceId;
        }

        public Builder addMetricName(String metricName) {
            if (metricName != null && !metricName.isBlank()) {
                metricNames.add(metricName.trim());
            }
            return this;
        }

        public Builder addDimensionName(String dimensionName) {
            if (dimensionName != null && !dimensionName.isBlank()) {
                dimensionNames.add(dimensionName.trim());
            }
            return this;
        }

        /** 登记一个数值证据（规范化后由 verifier 统一归一） */
        public Builder addNumber(String raw) {
            if (raw != null) {
                String n = raw.trim();
                if (!n.isEmpty() && !"null".equalsIgnoreCase(n)) {
                    numberTokens.add(n);
                }
            }
            return this;
        }

        public Builder withRowCount(int rowCount) {
            this.rowCount = rowCount;
            return this;
        }

        public MetricQueryEvidence build() {
            return new MetricQueryEvidence(datasourceId, List.copyOf(metricNames),
                    List.copyOf(dimensionNames), numberTokens, rowCount);
        }
    }
}
