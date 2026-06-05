package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 知识库处理触发结果
 */
@Data
public class KbProcessResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 入队数量 */
    private int queued;

    /** 是否强制重新处理 */
    private boolean force;
}
