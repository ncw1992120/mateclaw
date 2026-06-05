package vip.mate.client.model.request;

import lombok.Data;
import vip.mate.client.model.MessageContentPart;

import java.io.Serializable;
import java.util.List;

/**
 * 流式对话请求
 */
@Data
public class ChatStreamReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Agent ID */
    private Long agentId;

    /** 消息内容 */
    private String message;

    /** 会话 ID（默认 "default"） */
    private String conversationId = "default";

    /** 结构化内容片段（含图片等附件） */
    private List<MessageContentPart> contentParts;

    /** true 表示断线重连，不发送新消息，只附着到已有的流 */
    private Boolean reconnect;

    /** 客户端已处理的最后 SSE 事件 ID（reconnect=true 时有效） */
    private Long lastEventId;

    /** 思考深度：off / low / medium / high / max，null 表示跟随 Agent 默认 */
    private String thinkingLevel;

    /** 用户选择的模型 Provider ID（null 表示使用默认） */
    private String modelProvider;

    /** 用户选择的模型名称（null 表示使用默认） */
    private String modelName;
}
