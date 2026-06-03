package vip.mate.dataagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAgentAutoConfiguration;

import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.McpToolCallbackAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.StdioTransportAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.annotations.McpClientAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.client.httpclient.autoconfigure.SseHttpClientTransportAutoConfiguration;
import org.springframework.ai.mcp.client.httpclient.autoconfigure.StreamableHttpHttpClientTransportAutoConfiguration;

import vip.mate.sdk.config.MateClawRuntimeAutoConfiguration;

/**
 * DataAgent 应用启动类
 * <p>
 * 作为 MateClaw 运行时的宿主应用，通过 SDK 引入核心服务，
 * 提供专业的数据分析 Agent 工作台后端 API。
 */
@SpringBootApplication(
        scanBasePackages = "vip.mate.dataagent",
        exclude = {
                McpClientAutoConfiguration.class,
                McpToolCallbackAutoConfiguration.class,
                StdioTransportAutoConfiguration.class,
                McpClientAnnotationScannerAutoConfiguration.class,
                SseHttpClientTransportAutoConfiguration.class,
                StreamableHttpHttpClientTransportAutoConfiguration.class,
                DashScopeAgentAutoConfiguration.class,
        }
)
@Import(MateClawRuntimeAutoConfiguration.class)
public class DataAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataAgentApplication.class, args);
    }
}
