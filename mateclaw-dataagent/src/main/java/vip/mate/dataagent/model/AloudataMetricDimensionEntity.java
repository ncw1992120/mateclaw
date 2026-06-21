package vip.mate.dataagent.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Aloudata 指标-维度关联关系实体
 * <p>
 * 存储指标与维度的关联关系，来自 Aloudata metric_available_dimensions API。
 * 一个指标可以关联多个维度，支持检索指标时附带展示可用维度列表。
 */
@Data
@TableName("dataagent_aloudata_metric_dimension")
public class AloudataMetricDimensionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联数据源 ID */
    private Long datasourceId;

    /** 指标英文名 */
    private String metricName;

    /** 维度英文名 */
    private String dimName;

    /** 维度展示名 */
    private String dimDisplayName;

    /** 维度数据类型 */
    private String originDataType;

    /** 同步版本号 */
    private Integer syncVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
