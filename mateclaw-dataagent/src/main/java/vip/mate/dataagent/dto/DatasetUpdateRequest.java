package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 更新数据集请求
 */
@Data
public class DatasetUpdateRequest {

    /** 数据集名称 */
    private String name;

    /** 描述 */
    private String description;
}
