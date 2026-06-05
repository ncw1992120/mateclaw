package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * OpenAI Device Flow 取消请求
 */
@Data
public class OAuthDeviceCancelReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Device Auth ID */
    private String deviceAuthId;
}