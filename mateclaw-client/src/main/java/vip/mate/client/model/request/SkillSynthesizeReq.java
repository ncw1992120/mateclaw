package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 从对话合成技能请求
 */
@Data
public class SkillSynthesizeReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 会话 ID */
    private String conversationId;

    /** Agent ID */
    private Long agentId;
}