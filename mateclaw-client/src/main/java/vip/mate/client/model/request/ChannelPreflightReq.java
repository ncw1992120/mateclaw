package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 渠道预检请求
 */
@Data
public class ChannelPreflightReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 渠道类型 */
    private String channelType;

    /** JSON 格式的配置字符串 */
    private String configJson;
}
