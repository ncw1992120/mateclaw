package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.PageData;
import vip.mate.client.model.R;
import vip.mate.client.model.response.ConversationResp;
import vip.mate.client.model.response.ConversationStreamStatusResp;
import vip.mate.client.model.response.MessagePageResp;
import vip.mate.client.model.response.MessageResp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话管理客户端
 * <p>
 * 对应服务端 /api/v1/conversations 接口，提供会话的查询、删除、重命名、置顶、模型设置等功能
 */
public class ConversationClient extends AbstractApiClient {

    public ConversationClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取会话列表
     *
     * @return 会话列表
     */
    public R<List<ConversationResp>> list() {
        return get(ApiPathConstants.CONVERSATION, new ParameterizedTypeReference<R<List<ConversationResp>>>() {});
    }

    /**
     * 分页获取会话列表
     *
     * @param page    页码（从0开始）
     * @param size    每页大小
     * @param keyword 搜索关键词（可选）
     * @return 分页会话数据
     */
    public R<PageData<ConversationResp>> page(int page, int size, String keyword) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", page);
        params.put("size", size);
        if (keyword != null && !keyword.isBlank()) {
            params.put("keyword", keyword);
        }
        return get(ApiPathConstants.CONVERSATION_PAGE, params, new ParameterizedTypeReference<R<PageData<ConversationResp>>>() {});
    }

    /**
     * 获取会话消息列表
     *
     * @param conversationId 会话 ID
     * @param beforeId       起始消息 ID（可选，用于加载更早的消息）
     * @param limit          消息数量限制（可选）
     * @return 消息列表
     */
    /**
     * 获取全部消息（不分页）
     * 服务端 limit=null 时返回 List，适合消息量不大的场景
     */
    public R<List<MessageResp>> listMessages(String conversationId) {
        return get(resolvePath(ApiPathConstants.CONVERSATION_MESSAGES, conversationId), null,
                new ParameterizedTypeReference<R<List<MessageResp>>>() {});
    }

    /**
     * 获取消息（分页）
     * 服务端 limit>0 时返回 {messages, hasMore}
     *
     * @param limit    每页条数
     * @param beforeId 上拉加载时传上一批最早消息的 id，首次加载传 null
     */
    public R<MessagePageResp> listMessagesPage(String conversationId, Long beforeId, int limit) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("limit", limit);
        if (beforeId != null) {
            params.put("beforeId", beforeId);
        }
        return get(resolvePath(ApiPathConstants.CONVERSATION_MESSAGES, conversationId), params,
                new ParameterizedTypeReference<R<MessagePageResp>>() {});
    }

    /**
     * 删除会话
     *
     * @param conversationId 会话 ID
     * @return 操作结果
     */
    public R<Void> delete(String conversationId) {
        return delete(resolvePath(ApiPathConstants.CONVERSATION_BY_ID, conversationId), new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 重命名会话
     *
     * @param conversationId 会话 ID
     * @param title          新标题
     * @return 操作结果
     */
    public R<Void> rename(String conversationId, String title) {
        String path = resolvePath(ApiPathConstants.CONVERSATION_TITLE, conversationId) + "?title=" + title;
        return put(path, new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 设置会话置顶状态
     *
     * @param conversationId 会话 ID
     * @param pinned         是否置顶
     * @return 操作结果
     */
    public R<Void> setPinned(String conversationId, boolean pinned) {
        String path = resolvePath(ApiPathConstants.CONVERSATION_PIN, conversationId) + "?pinned=" + pinned;
        return put(path, new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 设置会话使用的模型
     *
     * @param conversationId 会话 ID
     * @param modelProvider  模型提供商
     * @param modelName      模型名称
     * @return 操作结果
     */
    public R<Void> setModel(String conversationId, String modelProvider, String modelName) {
        String path = resolvePath(ApiPathConstants.CONVERSATION_MODEL, conversationId) + "?modelProvider=" + modelProvider + "&modelName=" + modelName;
        return put(path, new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 批量删除会话
     *
     * @param conversationIds 会话 ID 列表
     * @return 删除的会话数量
     */
    public R<Integer> batchDelete(List<String> conversationIds) {
        return post(ApiPathConstants.CONVERSATION_BATCH_DELETE, conversationIds, new ParameterizedTypeReference<R<Integer>>() {});
    }

    /**
     * 清空会话消息
     *
     * @param conversationId 会话 ID
     * @return 操作结果
     */
    public R<Void> clearMessages(String conversationId) {
        return delete(resolvePath(ApiPathConstants.CONVERSATION_MESSAGES, conversationId), new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 获取流状态
     *
     * @param conversationId 会话 ID
     * @return 流状态信息
     */
    public R<ConversationStreamStatusResp> getStreamStatus(String conversationId) {
        return get(resolvePath(ApiPathConstants.CONVERSATION_STATUS, conversationId), new ParameterizedTypeReference<R<ConversationStreamStatusResp>>() {});
    }
}
