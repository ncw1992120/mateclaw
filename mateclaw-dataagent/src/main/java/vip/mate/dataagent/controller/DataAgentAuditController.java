package vip.mate.dataagent.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vip.mate.audit.model.AuditEventEntity;
import vip.mate.common.result.R;
import vip.mate.dataagent.auth.annotation.RequireWorkspaceRole;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.sdk.service.AuditRuntime;

import java.time.LocalDateTime;

/**
 * 操作审计查询控制器
 * <p>
 * 查询当前工作区的操作审计记录（复用 mateclaw 内置 mate_audit_event 表）。
 * 工作区 admin 及以上可见；全局管理员自动放行。
 */
@RestController
@RequestMapping("/v1/audit-events")
@RequiredArgsConstructor
@Tag(name = "操作审计", description = "工作区操作审计事件查询接口")
public class DataAgentAuditController {

    private final AuditRuntime auditRuntime;
    private final WorkspaceGuard workspaceGuard;

    /**
     * 分页查询当前工作区的审计事件
     */
    @GetMapping
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    @Operation(summary = "分页查询审计事件", description = "按操作类型/资源类型/时间范围分页查询当前工作区的审计记录")
    public R<IPage<AuditEventEntity>> list(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(auditRuntime.listEvents(workspaceGuard.currentWorkspaceId(), action, resourceType,
                startTime, endTime, page, size));
    }
}
