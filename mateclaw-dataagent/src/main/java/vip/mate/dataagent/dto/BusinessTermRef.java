package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 业务术语关联引用
 * <p>
 * 描述业务术语与 Aloudata 指标 / 维度之间的关联关系。
 * 指标 / 维度在全量同步时主键 ID 会变化，因此以 JSON 快照方式存储，
 * 以 datasourceId + name（metricName / dimName）作为稳定标识。
 */
@Data
public class BusinessTermRef {

    /** 指标 / 维度记录 ID（同步快照，仅辅助展示） */
    private Long id;

    /** 关联数据源 ID */
    private Long datasourceId;

    /** 数据源名称 */
    private String datasourceName;

    /** 指标英文名 / 维度英文名（稳定标识） */
    private String name;

    /** 指标展示名 / 维度中文名 */
    private String displayName;
}
