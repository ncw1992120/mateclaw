package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 插件实体响应
 */
@Data
public class PluginResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String version;
    private String pluginType;
    private String displayName;
    private String description;
    private String author;
    private String entrypoint;
    private String jarPath;
    private String configJson;
    private Boolean enabled;
    private String status;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
