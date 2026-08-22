package vip.mate.dataagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vip.mate.dataagent.model.QueryStateEntity;
import vip.mate.dataagent.repository.QueryStateMapper;
import vip.mate.dataagent.service.QueryStateService;

import java.util.List;

/**
 * 会话级「成功查询基座」状态服务实现（P0-2）。
 * <p>
 * 单点写入（{@code INSERT ... ON DUPLICATE KEY UPDATE} 语义）：
 * 同一 {@code (conversationId, datasourceId)} 只保留最新成功查询的基座。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryStateServiceImpl implements QueryStateService {

    private final QueryStateMapper queryStateMapper;

    @Override
    public void upsert(QueryStateEntity state) {
        if (state == null || state.getConversationId() == null || state.getConversationId().isBlank()
                || state.getDatasourceId() == null) {
            return;
        }
        try {
            QueryStateEntity existing = queryStateMapper.selectOne(
                    new LambdaQueryWrapper<QueryStateEntity>()
                            .eq(QueryStateEntity::getConversationId, state.getConversationId())
                            .eq(QueryStateEntity::getDatasourceId, state.getDatasourceId()));
            if (existing == null) {
                state.setQueryCount(state.getQueryCount() != null ? state.getQueryCount() : 1);
                queryStateMapper.insert(state);
            } else {
                existing.setMetrics(state.getMetrics());
                existing.setDimensions(state.getDimensions());
                existing.setTimeConstraint(state.getTimeConstraint());
                existing.setFilters(state.getFilters());
                existing.setOrders(state.getOrders());
                existing.setMetricDisplayMap(state.getMetricDisplayMap());
                existing.setRequestJson(state.getRequestJson());
                existing.setQueryCount(existing.getQueryCount() != null ? existing.getQueryCount() + 1 : 1);
                queryStateMapper.updateById(existing);
            }
        } catch (Exception e) {
            // 基座持久化失败不影响主链路（问数仍可继续），仅记录告警
            log.warn("[QueryState] upsert 失败 conversationId={}, datasourceId={}: {}",
                    state.getConversationId(), state.getDatasourceId(), e.getMessage());
        }
    }

    @Override
    public List<QueryStateEntity> listByConversation(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return List.of();
        }
        try {
            return queryStateMapper.selectList(
                    new LambdaQueryWrapper<QueryStateEntity>()
                            .eq(QueryStateEntity::getConversationId, conversationId)
                            .orderByDesc(QueryStateEntity::getUpdateTime));
        } catch (Exception e) {
            log.warn("[QueryState] 读取基座失败 conversationId={}: {}", conversationId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public void deleteByConversation(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        try {
            queryStateMapper.delete(new LambdaQueryWrapper<QueryStateEntity>()
                    .eq(QueryStateEntity::getConversationId, conversationId));
        } catch (Exception e) {
            log.warn("[QueryState] 删除会话基座失败 conversationId={}: {}", conversationId, e.getMessage());
        }
    }
}
