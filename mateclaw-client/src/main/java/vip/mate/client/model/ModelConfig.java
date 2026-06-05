package vip.mate.client.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 模型配置
 */
@Data
public class ModelConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String provider;
    private String modelName;
    private String description;
    private Double temperature;
    private Integer maxTokens;
    private Integer maxInputTokens;
    private Integer requestTimeoutSeconds;
    private Double topP;
    private Boolean enableSearch;
    private String searchStrategy;
    private Boolean builtin;
    private Boolean enabled;
    private Boolean isDefault;
    private String modelType;
    private String modalities;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
