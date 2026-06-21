package vip.mate.dataagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Elasticsearch 连接配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.elasticsearch")
public class ElasticsearchProperties {

    /** Elasticsearch 服务地址列表 */
    private String uris = "http://localhost:9200";

    /** 用户名（可选） */
    private String username;

    /** 密码（可选） */
    private String password;

    /** 连接超时时间（毫秒） */
    private int connectTimeout = 5000;

    /** 读取超时时间（毫秒） */
    private int readTimeout = 30000;
}
