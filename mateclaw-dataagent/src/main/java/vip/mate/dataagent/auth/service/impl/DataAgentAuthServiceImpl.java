package vip.mate.dataagent.auth.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vip.mate.auth.model.LoginRequest;
import vip.mate.auth.model.LoginResponse;
import vip.mate.auth.model.UserEntity;
import vip.mate.auth.service.AuthService;
import vip.mate.dataagent.auth.dto.DataAgentLoginResponse;
import vip.mate.dataagent.auth.service.DataAgentAuthService;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.workspace.core.model.WorkspaceWithRoleVO;
import vip.mate.workspace.core.service.WorkspaceService;

import java.util.List;

/**
 * DataAgent 认证服务实现
 * <p>
 * 委托 mateclaw-server 的 {@link AuthService} 完成密码校验与 JWT 签发，
 * 委托 {@link WorkspaceService} 查询用户可见的工作区列表。
 */
@Service
@RequiredArgsConstructor
public class DataAgentAuthServiceImpl implements DataAgentAuthService {

    private final AuthService authService;
    private final WorkspaceService workspaceService;

    @Override
    public DataAgentLoginResponse login(LoginRequest request) {
        LoginResponse base = authService.login(request);
        return buildResponse(base.getId(), base.getToken(), base.getUsername(),
                base.getNickname(), base.getRole());
    }

    @Override
    public DataAgentLoginResponse getCurrentUserInfo(String username) {
        UserEntity user = authService.findByUsername(username);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            return null;
        }
        return buildResponse(user.getId(), null, user.getUsername(),
                user.getNickname(), user.getRole());
    }

    /**
     * 组装登录响应（含工作区列表）
     */
    private DataAgentLoginResponse buildResponse(Long id, String token, String username,
                                                  String nickname, String role) {
        boolean isGlobalAdmin = DataAgentConstants.ROLE_ADMIN.equalsIgnoreCase(role);
        List<WorkspaceWithRoleVO> workspaces = workspaceService.listWithRoleByUserId(id, isGlobalAdmin);

        DataAgentLoginResponse response = new DataAgentLoginResponse();
        response.setId(id);
        response.setToken(token);
        response.setUsername(username);
        response.setNickname(nickname);
        response.setRole(role);
        response.setWorkspaces(workspaces);
        return response;
    }
}
