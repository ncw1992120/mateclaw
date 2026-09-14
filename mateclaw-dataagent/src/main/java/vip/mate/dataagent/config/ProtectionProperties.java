package vip.mate.dataagent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 服务保护配置属性（限流 / 熔断 / 降级相关阈值）
 * <p>
 * 纯配置持有类，不含业务处理逻辑。业务规则见 {@code UserAdmissionServiceImpl}
 * 与 {@code AloudataApiClient}。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = ProtectionProperties.CONFIG_PREFIX)
public class ProtectionProperties {

    public static final String CONFIG_PREFIX = "dataagent.protection";

    /** 对话流准入配置（/v1/chat/stream） */
    private final Chat chat = new Chat();

    /** 高成本端点限频配置（提示词优化 / 洞察生成等 LLM 写请求） */
    private final HighCost highCost = new HighCost();

    /** Aloudata API HTTP 超时配置 */
    private final Aloudata aloudata = new Aloudata();

    /**
     * 对话流准入参数
     */
    @Getter
    @Setter
    public static class Chat {

        /** 单用户每窗口最大对话请求数 */
        private int perUserRateLimit = 10;

        /** 限频统计窗口（秒） */
        private int perUserRateWindowSeconds = 60;

        /** 单用户最大并发 SSE 对话流数 */
        private int maxConcurrentPerUser = 3;
    }

    /**
     * 高成本端点限频参数
     */
    @Getter
    @Setter
    public static class HighCost {

        /** 单用户每窗口最大请求数 */
        private int perUserRateLimit = 20;

        /** 限频统计窗口（秒） */
        private int windowSeconds = 60;
    }

    /**
     * Aloudata API 超时参数
     */
    @Getter
    @Setter
    public static class Aloudata {

        /** 连接超时（毫秒） */
        private int connectTimeoutMs = 3000;

        /** 读取超时（毫秒），须大于 slow-call 阈值以触发熔断慢调用统计 */
        private int readTimeoutMs = 60000;
    }
}
