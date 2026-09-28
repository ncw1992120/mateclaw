package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vip.mate.common.result.R;
import vip.mate.dataagent.auth.annotation.RequireGlobalAdmin;
import vip.mate.dataagent.auth.annotation.RequireWorkspaceRole;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.UserUidMappingStatusVO;
import vip.mate.dataagent.dto.UserUidSyncResultDTO;
import vip.mate.dataagent.service.UserUidMappingService;
import vip.mate.dataagent.service.UserUidMappingSyncService;

import java.util.List;

/**
 * 用户 Aloudata UID 映射控制器
 * <p>
 * 提供「登录名 + 租户 → UID」自动映射的状态查询与管理员手动同步触发；
 * 映射数据由定时任务从外部用户系统同步，用户问数时自动生效，无需手动绑定查询账号。
 */
@RestController
@RequestMapping("/v1/user-uid-mappings")
@RequiredArgsConstructor
@Tag(name = "用户 UID 映射", description = "用户 Aloudata UID 自动映射状态查询与同步管理接口")
public class DataAgentUserUidMappingController {

    private final UserUidMappingService userUidMappingService;
    private final UserUidMappingSyncService userUidMappingSyncService;

    /**
     * 查询当前用户的自动映射状态列表（租户维度）
     */
    @GetMapping("/status")
    @Operation(summary = "查询自动映射状态", description = "获取当前用户全部启用的 UID 自动映射概要（租户 + 最近同步时间），供数据源页面按租户匹配展示「已自动同步」状态")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    public R<List<UserUidMappingStatusVO>> status() {
        return R.ok(userUidMappingService.listMyEnabledMappings());
    }

    /**
     * 管理员手动触发一次全量同步
     */
    @PostMapping("/sync")
    @Operation(summary = "手动触发同步", description = "管理员手动触发一次从外部用户系统到本地 UID 映射表的全量同步，用于首次初始化或排障；返回同步统计结果")
    @RequireGlobalAdmin
    public R<UserUidSyncResultDTO> sync() {
        return R.ok(userUidMappingSyncService.syncFromSource());
    }
}
