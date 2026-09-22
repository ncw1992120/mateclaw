package vip.mate.dataagent.service;

import vip.mate.dataagent.dto.QueryContextDTO;

import java.util.Map;

/**
 * 结果集查询：对 Python 执行的完整结果（内联或 ObjectRef）执行服务端有界筛选、排序和分页，
 * 返回 totalCount?/page/pageSize/rows。不基于旧接口前 10 行截断数据给出分页答案。
 */
public interface ResultSetQueryService {

    /**
     * @param context 携带 sort/pagination 的查询上下文（requestId 用于链路追踪）
     */
    Map<String, Object> preview(String executionId, QueryContextDTO context);
}
