package vip.mate.sdk.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vip.mate.audit.model.AuditEventEntity;
import vip.mate.audit.service.AuditEventService;
import vip.mate.sdk.service.AuditRuntime;

import java.time.LocalDateTime;

/**
 * 操作审计运行时实现
 * <p>
 * 全部委托给 MateClaw 内置 {@link AuditEventService}：
 * 在请求线程上捕获身份/请求上下文后异步落库，不阻塞业务请求。
 */
@Service
@RequiredArgsConstructor
public class AuditRuntimeImpl implements AuditRuntime {

    private final AuditEventService auditEventService;

    @Override
    public void record(String action, String resourceType, String resourceId,
                       String resourceName, String detailJson) {
        auditEventService.record(action, resourceType, resourceId, resourceName, detailJson);
    }

    @Override
    public void record(String action, String resourceType, String resourceId,
                       String resourceName, String detailJson, Long workspaceId) {
        auditEventService.record(action, resourceType, resourceId, resourceName, detailJson, workspaceId);
    }

    @Override
    public IPage<AuditEventEntity> listEvents(Long workspaceId, String action, String resourceType,
                                              LocalDateTime startTime, LocalDateTime endTime,
                                              int page, int size) {
        return auditEventService.listEvents(workspaceId, action, resourceType,
                startTime, endTime, page, size);
    }
}
