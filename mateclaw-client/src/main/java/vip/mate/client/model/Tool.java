package vip.mate.client.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 工具实体
 */
@Data
public class Tool implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String displayName;
    private String description;
    private String toolType;
    private String beanName;
    private String icon;
    private String mcpEndpoint;
    private String paramsSchema;
    private Boolean enabled;
    private Boolean builtin;
    private Long channelId;
    private String disclosureTier;
    private List<String> runtimeNames;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
