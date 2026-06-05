package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * 渠道状态信息
 */
@Data
public class ChannelStatusResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 活跃渠道数量 */
    private int activeCount;

    /** 支持的渠道类型集合 */
    private Set<String> supportedTypes;

    /** 活跃渠道列表 */
    private List<ChannelInfoResp> channels;
}
