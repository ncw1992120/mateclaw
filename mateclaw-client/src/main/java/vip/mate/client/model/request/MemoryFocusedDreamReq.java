package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 聚焦梦境触发请求
 */
@Data
public class MemoryFocusedDreamReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 梦境主题 */
    private String topic;
}
