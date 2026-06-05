package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 插件信息响应
 */
@Data
public class PluginInfoResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    /** 插件唯一标识 */
    private String name;
    private String version;
    /** tool / provider / channel / memory */
    private String pluginType;
    private String displayName;
    private String description;
    private String author;
    /** 完全限定入口类名 */
    private String entrypoint;
    /** JAR 文件路径 */
    private String jarPath;
    /** JSON 配置 */
    private String configJson;
    private Boolean enabled;
    /** LOADED / ENABLED / DISABLED / ERROR */
    private String status;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
    /** 运行时注册的工具列表 */
    private List<String> registeredTools;
    /** 运行时注册的渠道列表 */
    private List<String> registeredChannels;
    /** 运行时注册的 Provider */
    private String registeredProvider;
    /** 运行时注册的 Memory Provider */
    private String registeredMemoryProvider;
    /** 配置 Schema */
    private Map<String, Object> configSchema;
    /** 当前配置 */
    private Map<String, Object> currentConfig;
}
