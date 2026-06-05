package vip.mate.client.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Wiki 转换模板
 */
@Data
public class WikiTransformation implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long kbId;
    private Long workspaceId;
    private String name;
    private String title;
    private String description;
    private String promptTemplate;
    private Boolean applyDefault;
    private Long modelId;
    private Boolean enabled;
    private String outputTarget;
    private String outputFormat;
    private String outputSchema;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
