package vip.mate.client.model.request;

import lombok.Data;
import vip.mate.client.model.MessageContentPart;

import java.io.Serializable;
import java.util.List;

/**
 * 同步对话请求
 */
@Data
public class ChatReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 消息内容 */
    private String message;

    /** 会话 ID（默认 "default"） */
    private String conversationId = "default";

    /** 结构化内容片段（含图片等附件） */
    private List<MessageContentPart> contentParts;
}
