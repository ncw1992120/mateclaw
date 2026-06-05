package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Agent 运行时清理结果
 */
@Data
public class RuntimeSweepResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 成功回收的数量 */
    private int recycled;

    /** 被回收的会话 ID 列表 */
    private List<String> ids;
}
