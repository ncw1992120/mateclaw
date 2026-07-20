package vip.mate.sdk.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.RestController;
import vip.mate.MateClawApplication;
import vip.mate.sdk.properties.MateClawRuntimeProperties;

/**
 * MateClaw 嵌入式运行时自动配置
 * <p>
 * 当宿主应用将 mateclaw-sdk 作为依赖引入后，Spring Boot 会通过
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 自动加载本配置类，完成 MateClaw 核心组件的扫描和注册。
 *
 * <h3>宿主应用需排除的自动配置</h3>
 * <p>
 * 由于 MateClaw 内部未使用以下自动配置，宿主应用需在其
 * {@code @SpringBootApplication} 注解的 {@code exclude} 属性中排除它们，
 * 以避免启动时因缺少必要配置项而失败：
 * <pre>
 * &#064;SpringBootApplication(exclude = {
 *     org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration.class,
 *     org.springframework.ai.mcp.client.common.autoconfigure.McpToolCallbackAutoConfiguration.class,
 *     org.springframework.ai.mcp.client.common.autoconfigure.StdioTransportAutoConfiguration.class,
 *     org.springframework.ai.mcp.client.common.autoconfigure.annotations.McpClientAnnotationScannerAutoConfiguration.class,
 *     org.springframework.ai.mcp.client.httpclient.autoconfigure.SseHttpClientTransportAutoConfiguration.class,
 *     org.springframework.ai.mcp.client.httpclient.autoconfigure.StreamableHttpHttpClientTransportAutoConfiguration.class,
 *     com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAgentAutoConfiguration.class,
 * })
 * </pre>
 */
@Configuration
@ComponentScan(
        basePackages = "vip.mate",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = RestController.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = MateClawApplication.class)
        }
)
@MapperScan("vip.mate.**.repository")
@EnableScheduling
@EnableConfigurationProperties(MateClawRuntimeProperties.class)
public class MateClawRuntimeAutoConfiguration {

    /**
     * MyBatis Plus 分页拦截器
     * <p>
     * 数据库类型由 JDBC 连接在运行时自动检测，无需硬编码。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }
}
