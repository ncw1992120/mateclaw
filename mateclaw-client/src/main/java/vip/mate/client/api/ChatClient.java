package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.request.ChatInterruptReq;
import vip.mate.client.model.request.ChatReq;
import vip.mate.client.model.request.ChatStreamReq;
import vip.mate.client.model.response.ChatInterruptResp;
import vip.mate.client.model.response.ChatStopResp;
import vip.mate.client.model.response.ToolApprovalResp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chat 对话客户端
 * <p>
 * 对应服务端 /api/v1/chat 接口，提供流式对话、同步对话、文件上传下载等功能
 */
public class ChatClient extends AbstractApiClient {

    public ChatClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * SSE 流式对话
     * <p>
     * 通过回调处理流式事件
     *
     * @param request  流式对话请求参数
     * @param callback SSE 事件回调
     */
    public void chatStream(ChatStreamReq request, SseStreamCallback callback) {
        postForSseStream(ApiPathConstants.CHAT_STREAM, request, callback);
    }

    /**
     * 停止流式对话
     *
     * @param conversationId 会话 ID
     * @return 操作结果
     */
    public R<ChatStopResp> stop(String conversationId) {
        return post(resolvePath(ApiPathConstants.CHAT_STOP, conversationId), null,
                new ParameterizedTypeReference<R<ChatStopResp>>() {});
    }

    /**
     * 中断对话
     *
     * @param conversationId 会话 ID
     * @param request        中断请求参数
     * @return 操作结果
     */
    public R<ChatInterruptResp> interrupt(String conversationId, ChatInterruptReq request) {
        return post(resolvePath(ApiPathConstants.CHAT_INTERRUPT, conversationId), request,
                new ParameterizedTypeReference<R<ChatInterruptResp>>() {});
    }

    /**
     * 同步对话
     *
     * @param agentId Agent ID
     * @param request 对话请求参数
     * @return 对话响应内容
     */
    public R<String> chat(Long agentId, ChatReq request) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("agentId", agentId);
        return post(ApiPathConstants.CHAT, params, request,
                new ParameterizedTypeReference<R<String>>() {});
    }

    /**
     * 获取待审批列表
     *
     * @param conversationId 会话 ID
     * @return 待审批列表
     */
    public R<List<ToolApprovalResp>> getPendingApprovals(String conversationId) {
        return get(resolvePath(ApiPathConstants.CHAT_PENDING_APPROVALS, conversationId),
                new ParameterizedTypeReference<R<List<ToolApprovalResp>>>() {});
    }
}
