package vip.mate.sdk.service.workspace.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vip.mate.auth.model.UserEntity;
import vip.mate.auth.service.AuthService;
import vip.mate.exception.MateClawException;
import vip.mate.sdk.service.workspace.WorkspaceRuntime;
import vip.mate.workspace.core.model.WorkspaceEntity;
import vip.mate.workspace.core.model.WorkspaceMemberEntity;
import vip.mate.workspace.core.model.WorkspaceWithRoleVO;
import vip.mate.workspace.core.service.WorkspaceService;

import java.util.List;
import java.util.Locale;

/**
 * 工作区运行时实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceRuntimeImpl implements WorkspaceRuntime {

    private final WorkspaceService workspaceService;
    private final AuthService authService;

    @Override
    public void requireWorkspaceRole(Long workspaceId, Long userId, String minRole) {
        workspaceService.requirePermission(workspaceId, userId, minRole);
    }

    @Override
    public boolean hasWorkspacePermission(Long workspaceId, Long userId, String minRole) {
        return workspaceService.hasPermissionCached(workspaceId, userId, minRole);
    }

    @Override
    public boolean isGlobalAdmin(Long userId) {
        UserEntity user = authService.findById(userId);
        return user != null && "admin".equalsIgnoreCase(user.getRole());
    }

    @Override
    public String getWorkspaceMemberRole(Long workspaceId, Long userId) {
        WorkspaceMemberEntity member = workspaceService.getMembership(workspaceId, userId);
        return member != null ? member.getRole() : null;
    }

    @Override
    public List<WorkspaceWithRoleVO> listWorkspacesWithRole(Long userId, boolean isGlobalAdmin) {
        return workspaceService.listWithRoleByUserId(userId, isGlobalAdmin);
    }

    @Override
    public WorkspaceEntity getWorkspace(Long id) {
        return workspaceService.getById(id);
    }

    @Override
    public WorkspaceEntity createWorkspace(WorkspaceEntity entity, Long creatorUserId) {
        return workspaceService.create(entity, creatorUserId);
    }

    @Override
    public WorkspaceEntity updateWorkspace(WorkspaceEntity entity) {
        return workspaceService.update(entity);
    }

    @Override
    public void deleteWorkspace(Long id) {
        workspaceService.delete(id);
    }

    /**
     * 获取工作区成员列表（含用户名、昵称）
     */
    @Override
    public List<WorkspaceMemberEntity> listWorkspaceMembers(Long workspaceId) {
        List<WorkspaceMemberEntity> members = workspaceService.listMembers(workspaceId);
        enrichWithUserInfo(members);
        return members;
    }

    /**
     * 分页查询工作区成员（含用户名、昵称），支持按用户名/昵称关键词与角色过滤
     */
    @Override
    public IPage<WorkspaceMemberEntity> pageWorkspaceMembers(Long workspaceId, int page, int size,
                                                             String keyword, String role) {
        List<WorkspaceMemberEntity> members = workspaceService.listMembers(workspaceId);
        enrichWithUserInfo(members);

        String kw = keyword != null ? keyword.trim().toLowerCase(Locale.ROOT) : "";
        String roleFilter = role != null ? role.trim().toLowerCase(Locale.ROOT) : "";
        List<WorkspaceMemberEntity> filtered = members.stream()
                .filter(m -> kw.isEmpty() || containsIgnoreCase(m.getUsername(), kw)
                        || containsIgnoreCase(m.getNickname(), kw))
                .filter(m -> roleFilter.isEmpty()
                        || (m.getRole() != null && roleFilter.equals(m.getRole().toLowerCase(Locale.ROOT))))
                .toList();

        long total = filtered.size();
        int fromIndex = (long) (page - 1) * size > total
                ? (int) total : Math.max(0, (page - 1) * size);
        int toIndex = Math.min(fromIndex + size, (int) total);
        List<WorkspaceMemberEntity> records = fromIndex >= toIndex
                ? List.of() : filtered.subList(fromIndex, toIndex);

        Page<WorkspaceMemberEntity> result = new Page<>(page, size, total);
        result.setRecords(records);
        return result;
    }

    private static boolean containsIgnoreCase(String value, String lowerKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerKeyword);
    }

    /**
     * 批量为成员填充用户名、昵称
     */
    private void enrichWithUserInfo(List<WorkspaceMemberEntity> members) {
        for (WorkspaceMemberEntity m : members) {
            UserEntity user = authService.findById(m.getUserId());
            if (user != null) {
                m.setUsername(user.getUsername());
                m.setNickname(user.getNickname());
            }
        }
    }

    /**
     * 添加工作区成员
     * <p>
     * 若用户不存在则创建账号，已有用户直接加入。密码仅用于新账号创建，
     * 不会重置已有用户的密码（避免工作区管理员借此接管其他账号）。
     */
    @Override
    public WorkspaceMemberEntity addWorkspaceMember(Long workspaceId, String username, String nickname,
                                                     String password, String role) {
        UserEntity target = authService.findByUsername(username);
        if (target == null) {
            if (password == null || password.isBlank()) {
                throw new MateClawException("err.workspace.user_not_found",
                        "用户不存在: " + username + "，需提供密码以创建账号");
            }
            UserEntity newUser = new UserEntity();
            newUser.setUsername(username);
            newUser.setPassword(password);
            newUser.setNickname(nickname != null && !nickname.isBlank() ? nickname : username);
            target = authService.createUser(newUser);
        }
        return workspaceService.addMember(workspaceId, target.getId(), role);
    }

    @Override
    public WorkspaceMemberEntity updateWorkspaceMemberRole(Long workspaceId, Long userId, String role) {
        return workspaceService.updateMemberRole(workspaceId, userId, role);
    }

    @Override
    public void removeWorkspaceMember(Long workspaceId, Long userId) {
        workspaceService.removeMember(workspaceId, userId);
    }

    @Override
    public Long findUserIdByUsername(String username) {
        UserEntity user = authService.findByUsername(username);
        return user != null ? user.getId() : null;
    }
}
