package vip.mate.dataagent.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Elasticsearch 客户端配置
 * <p>
 * 基于 elasticsearch-java 8.x 客户端，创建 ElasticsearchClient Bean。
 * 当 spring.elasticsearch.uris 配置存在时自动生效。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ElasticsearchConfig {

    private final ElasticsearchProperties properties;

    /** 默认保活时间（毫秒） */
    private static final long DEFAULT_KEEP_ALIVE_MS = 30_000L;

    /**
     * 创建 Elasticsearch 低级 RestClient
     */
    @Bean
    @ConditionalOnMissingBean
    public RestClient elasticsearchRestClient() {
        String[] uriArray = properties.getUris().split(",");
        HttpHost[] hosts = new HttpHost[uriArray.length];
        for (int i = 0; i < uriArray.length; i++) {
            hosts[i] = HttpHost.create(uriArray[i].trim());
        }

        RestClientBuilder builder = RestClient.builder(hosts);

        /* 认证配置 */
        if (StringUtils.hasText(properties.getUsername()) && StringUtils.hasText(properties.getPassword())) {
            builder.setDefaultHeaders(new Header[]{
                    new BasicHeader("Authorization",
                            "Basic " + java.util.Base64.getEncoder()
                                    .encodeToString((properties.getUsername() + ":" + properties.getPassword()).getBytes()))
            });
        }

        /* 超时配置 */
        builder.setRequestConfigCallback(requestConfigBuilder ->
                requestConfigBuilder
                        .setConnectTimeout(properties.getConnectTimeout())
                        .setSocketTimeout(properties.getReadTimeout())
        );

        log.info("Elasticsearch RestClient 初始化完成，连接地址: {}", properties.getUris());
        return builder.build();
    }

    /**
     * 创建 Elasticsearch 高级客户端
     */
    @Bean
    @ConditionalOnMissingBean
    public ElasticsearchClient elasticsearchClient(RestClient restClient) {
        ObjectMapper objectMapper = new ObjectMapper();
        RestClientTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper(objectMapper));
        return new ElasticsearchClient(transport);
    }
}
