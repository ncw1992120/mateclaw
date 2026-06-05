package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 子 Agent 列表结果
 */
@Data
public class SubagentListResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 子代理列表 */
    private List<SubagentInfoResp> subagents;
}
