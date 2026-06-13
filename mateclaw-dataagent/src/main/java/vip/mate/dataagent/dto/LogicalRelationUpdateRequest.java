package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 更新逻辑外键关系请求
 */
@Data
public class LogicalRelationUpdateRequest {

    /** 关系类型：1:1 / 1:N / N:1 */
    private String relationType;

    /** 业务描述 */
    private String description;
}
