package vip.mate.dataagent.service;

import vip.mate.dataagent.model.QueryStateEntity;

import java.util.List;

/**
 * 会话级「成功查询基座」状态服务（P0-2）。
 * <p>
 * 写入/读取每轮成功指标查询的结构化参数，作为多轮追问的确定性基座，
 * 使追问与历史消息压缩解耦。
 */
public interface QueryStateService {

    /**
     * 写入/覆盖某会话在某数据源上的最新成功查询基座（upsert）。
     *
     * @param state 查询基座状态（conversationId + datasourceId 必须非空）
     */
    void upsert(QueryStateEntity state);

    /**
     * 读取某会话的全部成功查询基座。
     *
     * @param conversationId 会话 ID
     * @return 基座列表（按更新时间倒序），无则返回空列表
     */
    List<QueryStateEntity> listByConversation(String conversationId);

    /**
     * 删除某会话的全部基座（会话删除时清理）。
     *
     * @param conversationId 会话 ID
     */
    void deleteByConversation(String conversationId);
}
