package vip.mate.client.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Wiki 知识库
 */
@Data
public class WikiKnowledgeBase implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String description;
    private Long agentId;
    private String configContent;
    private String sourceDirectory;
    private String status;
    private Integer pageCount;
    private Integer rawCount;
    private Long workspaceId;
    private Long embeddingModelId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
