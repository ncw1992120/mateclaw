package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 设备码轮询请求
 */
@Data
public class DeviceCodePollReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 设备授权会话 ID */
    private String deviceAuthId;
}
