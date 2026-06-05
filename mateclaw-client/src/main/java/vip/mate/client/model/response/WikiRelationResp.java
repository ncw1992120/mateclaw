package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Wiki 页面关系响应
 */
@Data
public class WikiRelationResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long kbId;
    private Long pageAId;
    private Long pageBId;
    private BigDecimal totalScore;
    private String signalsJson;
    private String type;
    private String confidence;
    private String evidence;
    private Long evidenceRawId;
    private String source;
    private LocalDateTime computedAt;
    private String computedHash;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
