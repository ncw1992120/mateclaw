package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vip.mate.common.result.R;
import vip.mate.dataagent.auth.annotation.RequireWorkspaceRole;
import vip.mate.dataagent.auth.context.UserContextHolder;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.DatasourceAccountRequest;
import vip.mate.dataagent.dto.DatasourceAccountVO;
import vip.mate.dataagent.service.DatasourceAccountService;

import java.util.List;

/**
 * 数据源用户查询账号管理控制器
 * <p>
 * 每个用户可以为自己绑定的数据源配置独立的查询账号，
 * 查询时优先使用用户自己的查询账号，而非数据源的管理员同步账号。
 */
@RestController
@RequestMapping("/v1/datasource-accounts")
@RequiredArgsConstructor
@Tag(name = "数据源查询账号", description = "用户数据源查询账号绑定管理接口")
public class DatasourceAccountController {

    private final DatasourceAccountService datasourceAccountService;

    /**
     * 查询当前用户所有已绑定的查询账号
     */
    @GetMapping
    @Operation(summary = "查询账号列表", description = "获取当前用户所有已绑定的数据源查询账号")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    public R<List<DatasourceAccountVO>> list() {
        Long userId = UserContextHolder.getUserId();
        return R.ok(datasourceAccountService.listByUserId(userId));
    }

    /**
     * 查询当前用户在指定数据源上绑定的查询账号
     */
    @GetMapping("/{datasourceId}")
    @Operation(summary = "查询指定数据源账号", description = "获取当前用户在指定数据源上绑定的查询账号")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    public R<DatasourceAccountVO> get(
            @Parameter(description = "数据源 ID") @PathVariable Long datasourceId) {
        Long userId = UserContextHolder.getUserId();
        var entity = datasourceAccountService.getByDatasourceIdAndUserId(datasourceId, userId);
        if (entity == null) {
            return R.ok(null);
        }
        // 手动转 VO 避免在 service 层重复查询
        DatasourceAccountVO vo = new DatasourceAccountVO();
        vo.setId(entity.getId());
        vo.setDatasourceId(entity.getDatasourceId());
        vo.setQueryUsername(entity.getQueryUsername());
        vo.setStatus(entity.getStatus());
        vo.setLastTestOk(entity.getLastTestOk());
        return R.ok(vo);
    }

    /**
     * 创建或更新当前用户的查询账号绑定
     */
    @PostMapping
    @Operation(summary = "绑定查询账号", description = "为当前用户创建或更新数据源查询账号绑定，查询时优先使用此账号")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    public R<DatasourceAccountVO> upsert(@RequestBody DatasourceAccountRequest request) {
        Long userId = UserContextHolder.requireUserId();
        return R.ok(datasourceAccountService.upsertAccount(request, userId));
    }

    /**
     * 删除当前用户的查询账号绑定
     */
    @DeleteMapping("/{datasourceId}")
    @Operation(summary = "解绑查询账号", description = "删除当前用户在指定数据源上的查询账号绑定")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    public R<Void> delete(
            @Parameter(description = "数据源 ID") @PathVariable Long datasourceId) {
        Long userId = UserContextHolder.getUserId();
        datasourceAccountService.deleteAccount(datasourceId, userId);
        return R.ok(null);
    }

    /**
     * 测试当前用户的查询账号连接
     */
    @PostMapping("/{datasourceId}/test")
    @Operation(summary = "测试查询账号连接", description = "测试当前用户在指定数据源上的查询账号是否可以成功连接")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    public R<Boolean> testConnection(
            @Parameter(description = "数据源 ID") @PathVariable Long datasourceId) {
        Long userId = UserContextHolder.getUserId();
        return R.ok(datasourceAccountService.testAccountConnection(datasourceId, userId));
    }
}
