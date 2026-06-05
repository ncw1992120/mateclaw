package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 渠道健康检查结果
 */
@Data
public class ChannelHealthResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 渠道类型 */
    private String channelType;

    /** 渠道 ID */
    private Long channelId;

    /** 状态 (UP/RECONNECTING/DOWN/OUT_OF_SERVICE/UNKNOWN) */
    private String status;

    /** 详情 */
    private String detail;

    /** 最后事件时间 */
    private String lastEventAt;

    /** 名称 */
    private String name;

    /** 是否启用 */
    private boolean enabled;

    /** 渠道身份信息 */
    private Map<String, Object> identity;
}
