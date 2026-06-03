package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 模型激活请求
 */
@Data
public class ModelActiveRequest {

    /** 模型配置 ID */
    private Long modelId;
}