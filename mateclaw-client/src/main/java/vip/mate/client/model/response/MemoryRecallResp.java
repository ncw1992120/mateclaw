package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 记忆召回记录
 */
@Data
public class MemoryRecallResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long agentId;
    private String filename;
    private String snippetHash;
    private String snippetPreview;
    private Integer recallCount;
    private Integer dailyCount;
    private String queryHashes;
    private Double score;
    private LocalDateTime lastRecalledAt;
    private Boolean promoted;
    private Integer reviewCount;
    private LocalDateTime lastReviewedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
