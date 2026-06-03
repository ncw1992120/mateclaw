package vip.mate.dataagent.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dataagent_dataset_data")
public class DatasetDataEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long datasetId;

    private String rowData;

    private String rowHash;

    private Integer sourceRowNumber;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;
}
