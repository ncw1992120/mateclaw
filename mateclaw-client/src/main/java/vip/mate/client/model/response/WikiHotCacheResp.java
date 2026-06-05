package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Wiki 热缓存
 */
@Data
public class WikiHotCacheResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long kbId;
    private String content;
    private String contentHash;
    private LocalDateTime lastUpdated;
    private String updateReason;
    private Long rebuildCount;
    private LocalDateTime lastRebuildStartedAt;
    private Long lastRebuildDurationMs;
    private String lastRebuildError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
