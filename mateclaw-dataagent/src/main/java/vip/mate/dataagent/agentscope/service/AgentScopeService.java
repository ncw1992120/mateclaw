package vip.mate.dataagent.agentscope.service;

import io.agentscope.core.agent.Event;
import reactor.core.publisher.Flux;
import vip.mate.dataagent.agentscope.dto.AgentCallRequest;
import vip.mate.dataagent.agentscope.dto.AgentCallResponse;

/**
 * AgentScope Agent 调用服务接口
 * <p>
 * 封装 AgentScope Java SDK 的 Agent 流式调用能力，
 * 模型配置通过后端已有的模型管理接口获取。
 */
public interface AgentScopeService {

    /**
     * 流式调用 AgentScope Agent
     * <p>
     * 根据请求中的模型配置（modelProvider + modelName）或后端激活模型构建 AgentScope Agent，
     * 通过 Flux 流式推送 Agent 推理事件（文本增量、工具调用、最终结果等）。
     *
     * @param request 调用请求，包含用户消息、模型配置、会话信息等
     * @return AgentScope 事件流，包含 REASONING / TOOL_RESULT / AGENT_RESULT / SUMMARY 等事件类型
     */
    Flux<Event> streamCall(AgentCallRequest request);

    /**
     * 同步调用 AgentScope Agent（基于流式调用阻塞收集完整结果）
     * <p>
     * 内部通过 {@link #streamCall(AgentCallRequest)} 订阅完整事件流，
     * 收集最终 AGENT_RESULT 事件的消息内容后返回。
     * 适用于需要完整结果后再处理的场景（如仪表盘生成等）。
     *
     * @param request 调用请求，包含用户消息、模型配置、会话信息等
     * @return 调用响应，包含 Agent 回复内容、模型信息等
     */
    AgentCallResponse call(AgentCallRequest request);
}
