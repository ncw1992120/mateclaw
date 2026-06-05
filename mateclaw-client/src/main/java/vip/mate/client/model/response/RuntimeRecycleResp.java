package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * Agent 运行时回收结果
 */
@Data
public class RuntimeRecycleResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 是否已回收 */
    private boolean recycled;
}
