package vip.mate.dataagent.dto;

import lombok.Data;

import java.util.List;

import vip.mate.workspace.conversation.model.MessageContentPart;

@Data
public class ChatRequest {
    private Long agentId;
    private String conversationId;
    private String message;
    private String modelName;
    /** Provider ID of the model the user picked. Paired with {@link #modelName}; null means no per-conversation override. */
    private String modelProvider;
    /** Whether this is a reconnect request after stream interruption */
    private boolean reconnect;
    /** Last SSE event ID received before disconnection (for dedup replay) */
    private Long lastEventId;
    /**
     * 用户在前端勾选的数据源 ID 白名单。
     * <p>
     * 非空时，后端会在系统提示词中告知 Agent 仅可使用这些数据源；
     * 同时 DatasourceQueryTool 会按此列表对 list_datasources / list_tables /
     * execute_sql / search_schema 等动作做兜底校验，禁止越权访问。
     * 为空或为 null 时表示不限制（由 LLM 自主选择）。
     */
    private List<String> datasourceIds;
    /** 结构化消息内容片段，包含附件信息等 */
    private List<MessageContentPart> contentParts;
}
