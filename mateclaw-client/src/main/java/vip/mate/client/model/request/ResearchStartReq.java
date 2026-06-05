package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * Wiki 研究启动请求
 */
@Data
public class ResearchStartReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 知识库 ID */
    private Long kbId;

    /** 研究主题 */
    private String topic;

    /** 每个问题的 topK 数 */
    private Integer topKPerQuestion;
}
