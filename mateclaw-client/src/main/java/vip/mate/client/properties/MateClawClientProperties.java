package vip.mate.client.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MateClaw 客户端配置属性
 */
@Data
@ConfigurationProperties(prefix = MateClawClientProperties.PREFIX)
public class MateClawClientProperties {

    public static final String PREFIX = "mateclaw.client";

    /** 服务端基础地址，如 <a href="http://localhost:8080">...</a> */
    private String baseUrl = "http://localhost:8080";

    /** 认证 Token（JWT 或 PAT，优先级低于 username/password） */
    private String token;

    /** 用户名（与 password 配合使用，自动登录获取 JWT） */
    private String username;

    /** 密码（与 username 配合使用） */
    private String password;

    /** 连接超时时间（毫秒） */
    private int connectTimeout = 5000;

    /** 读取超时时间（毫秒） */
    private int readTimeout = 30000;

    /** 默认工作区 ID */
    private Long defaultWorkspaceId = 1L;
}
