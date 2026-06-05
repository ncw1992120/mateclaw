package vip.mate.client.model.request;

import lombok.Data;
import vip.mate.client.model.MessageContentPart;

import java.io.Serializable;
import java.util.List;

/**
 * Chat 中断请求
 */
@Data
public class ChatInterruptReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 消息内容 */
    private String message;

    /** Agent ID */
    private Long agentId;

    /** 结构化内容片段 */
    private List<MessageContentPart> contentParts;
}