package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 设备码轮询结果
 */
@Data
public class DeviceCodePollResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 状态 (PENDING/COMPLETED/EXPIRED) */
    private String status;
}
