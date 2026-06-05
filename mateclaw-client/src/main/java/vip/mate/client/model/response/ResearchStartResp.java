package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * Wiki 研究启动结果
 */
@Data
public class ResearchStartResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** SSE 会话 ID */
    private String sessionId;

    /** 知识库 ID */
    private Long kbId;

    /** 研究主题 */
    private String topic;

    /** SSE 订阅 URL */
    private String streamUrl;
}
