package vip.mate.sdk.service.llm;

import reactor.core.publisher.Flux;
import vip.mate.sdk.service.llm.dto.LlmChatRequest;
import vip.mate.sdk.service.llm.dto.LlmChatResponse;

/**
 * 大模型直连运行时
 * <p>
 * 提供无状态的大模型对话调用（同步 + 流式），不做任何持久化操作
 * （不创建会话、不保存消息、不写库），等价于通过 HTTP 直接调用大模型 API。
 * <p>
 * 模型选择规则：请求可指定 {@code provider} / {@code model}（可单独或成对指定），
 * 缺省时回退到当前默认模型；不经过 Agent / 会话体系。
 */
public interface LlmRuntime {

    /**
     * 同步直连对话：直接调用大模型并返回完整回答。
     *
     * @param request 对话请求（messages 必填）
     * @return 完整回答及相关模型信息
     */
    LlmChatResponse chatDirect(LlmChatRequest request);

    /**
     * 流式直连对话：返回内容增量序列（逐 token 文本）。
     *
     * @param request 对话请求（messages 必填）
     * @return 内容增量流
     */
    Flux<String> chatDirectStream(LlmChatRequest request);
}
