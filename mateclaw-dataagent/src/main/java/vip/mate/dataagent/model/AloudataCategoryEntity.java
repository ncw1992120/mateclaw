package vip.mate.dataagent.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Aloudata 类目元数据实体
 * <p>
 * 存储从 Aloudata 指标平台同步的指标/维度类目信息，
 * 支持类目树结构（parent_id）。
 */
@Data
@TableName("dataagent_aloudata_category")
public class AloudataCategoryEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联数据源 ID */
    private Long datasourceId;

    /** 类目 ID（Aloudata 平台标识） */
    private String categoryId;

    /** 类目名称 */
    private String categoryName;

    /** 类目类型：CATEGORY_METRIC/CATEGORY_DIMENSION/CATEGORY_DATASET */
    private String categoryType;

    /** 父级类目 ID */
    private String parentId;

    /** 上级类目 ID（frontId） */
    private String frontId;

    /** 类型：SYSTEM（系统类目）/ null（用户自定义） */
    private String type;

    /** 同步版本号 */
    private Integer syncVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
