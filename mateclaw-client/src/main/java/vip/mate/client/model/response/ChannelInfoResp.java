package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 渠道信息
 */
@Data
public class ChannelInfoResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 渠道 ID */
    private Long id;

    /** 渠道类型 */
    private String type;

    /** 渠道名称 */
    private String name;

    /** 是否运行中 */
    private boolean running;

    /** 连接状态 */
    private String connectionState;

    /** 最后错误信息 */
    private String lastError;

    /** 重连尝试次数 */
    private int reconnectAttempts;

    /** 最后事件时间 */
    private String lastEventTime;

    /** 健康状态 */
    private String healthStatus;
}
