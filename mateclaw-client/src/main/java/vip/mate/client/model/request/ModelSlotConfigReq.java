package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 模型槽配置
 */
@Data
public class ModelSlotConfigReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Provider ID */
    private String providerId;

    /** 模型名称 */
    private String model;
}
