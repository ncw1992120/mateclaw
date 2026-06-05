package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * Agent 运行时停止结果
 */
@Data
public class RuntimeStopResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 是否已停止 */
    private boolean stopped;
}
