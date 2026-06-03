package vip.mate;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import reactor.core.publisher.Flux;
import vip.mate.agent.AgentService;
import vip.mate.dataagent.DataAgentApplication;

/**
 * Agent服务测试类
 *
 * @author jaywu
 * @version DemoTest.java, 2026年05月27日 16:59
 **/
@SpringBootTest(
        classes = DataAgentApplication.class,
        webEnvironment = WebEnvironment.NONE
)
public class DemoTest {

    @Autowired
    private AgentService agentService;

    @Test
    public void test() {
        Long agentId = 1000000001L;
        String conversationId = "111";
        String message = "注意，我指的是《盗墓笔记》小说是什么，它各章节剧情";
        Flux<AgentService.StreamDelta> streamDeltaFlux = agentService.chatStructuredStream(agentId, message, conversationId);
        System.out.println("===========测试开始===========");
        streamDeltaFlux
                .doOnNext(streamDelta -> {
                    String content = streamDelta.content();
                    if (StringUtils.isNotBlank(content)) {
                        System.out.print(content);
                    }
                })
                .blockLast();
        System.out.println("===========测试结束===========");
    }
}
