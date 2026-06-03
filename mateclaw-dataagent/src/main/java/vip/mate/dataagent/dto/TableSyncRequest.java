package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 单表同步请求
 */
@Data
public class TableSyncRequest {

    /** 同步模式：append=追加（仅新增字段），overwrite=覆盖（删除旧字段重新发现） */
    private String mode = "append";
}
