package vip.mate.sdk.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import vip.mate.audit.model.AuditEventEntity;

import java.time.LocalDateTime;

/**
 * 操作审计运行时接口
 * <p>
 * 将宿主应用的操作审计委托给 MateClaw 内置审计设施
 * （{@code mate_audit_event} 表 + {@code AuditEventService} 异步落库），
 * 宿主应用无需自建审计表即可获得统一的操作审计能力。
 * <p>
 * 用户身份（username/userId）、IP、User-Agent 由底层服务在请求线程上
 * 自动从 SecurityContext / RequestContext 捕获，调用方只需提供业务字段。
 */
public interface AuditRuntime {

    /**
     * 异步记录审计事件（workspace 取自请求头 X-Workspace-Id）
     *
     * @param action       操作类型：CREATE / UPDATE / DELETE / ENABLE / DISABLE / RUN 等
     * @param resourceType 资源类型：AGENT / DATASET / INSIGHT_DASHBOARD 等
     * @param resourceId   资源 ID（可为 null）
     * @param resourceName 资源名称（可为 null）
     * @param detailJson   变更详情 JSON（可为 null）
     */
    void record(String action, String resourceType, String resourceId,
                String resourceName, String detailJson);

    /**
     * 异步记录审计事件（显式指定 workspace ID，优先于请求头解析）
     */
    void record(String action, String resourceType, String resourceId,
                String resourceName, String detailJson, Long workspaceId);

    /**
     * 分页查询审计事件
     *
     * @param workspaceId  工作区 ID（null 则跨工作区）
     * @param action       操作类型过滤（可 null）
     * @param resourceType 资源类型过滤（可 null）
     * @param startTime    开始时间（可 null）
     * @param endTime      结束时间（可 null）
     * @param page         页码（1 起）
     * @param size         页大小
     */
    IPage<AuditEventEntity> listEvents(Long workspaceId, String action, String resourceType,
                                       LocalDateTime startTime, LocalDateTime endTime,
                                       int page, int size);
}
