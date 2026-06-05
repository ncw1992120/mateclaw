package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 设备码授权启动结果
 */
@Data
public class DeviceCodeStartResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 设备授权会话 ID */
    private String deviceAuthId;

    /** 用户输入码 */
    private String userCode;

    /** 验证 URL */
    private String verificationUrl;

    /** 含 user_code 的完整验证 URL */
    private String verificationUrlComplete;

    /** 建议轮询间隔秒数 */
    private int intervalSeconds;

    /** 过期时间秒数 */
    private int expiresInSeconds;
}
