package vip.mate.sdk.service.workspace;

import vip.mate.workspace.core.model.WorkspaceEntity;
import vip.mate.workspace.core.model.WorkspaceMemberEntity;
import vip.mate.workspace.core.model.WorkspaceWithRoleVO;

import java.util.List;

/**
 * 工作区运行时接口
 * <p>
 * 提供工作区管理、权限校验、成员管理等编程式访问能力。
 */
public interface WorkspaceRuntime {

    /**
     * 断言用户在指定工作区具有最低角色权限，不通过则抛 403 异常
     *
     * @param workspaceId 工作区 ID
     * @param userId      用户 ID
     * @param minRole     最低角色要求：owner &gt; admin &gt; member &gt; viewer
     */
    void requireWorkspaceRole(Long workspaceId, Long userId, String minRole);

    /**
     * 检查用户是否有指定工作区的最低角色权限（带缓存，高频调用场景使用）
     *
     * @param workspaceId 工作区 ID
     * @param userId      用户 ID
     * @param minRole     最低角色要求
     * @return true 如果用户有足够权限
     */
    boolean hasWorkspacePermission(Long workspaceId, Long userId, String minRole);

    /**
     * 判断用户是否为全局管理员（mate_user.role = admin）
     *
     * @param userId 用户 ID
     * @return true 如果是全局管理员
     */
    boolean isGlobalAdmin(Long userId);

    /**
     * 获取用户在指定工作区的成员角色
     *
     * @param workspaceId 工作区 ID
     * @param userId      用户 ID
     * @return 角色字符串（owner/admin/member/viewer），非成员返回 null
     */
    String getWorkspaceMemberRole(Long workspaceId, Long userId);

    /**
     * 查询用户可见的工作区列表（含成员角色与生效角色）
     *
     * @param userId        用户 ID
     * @param isGlobalAdmin 是否为全局管理员
     * @return 工作区列表（含角色信息）
     */
    List<WorkspaceWithRoleVO> listWorkspacesWithRole(Long userId, boolean isGlobalAdmin);

    /**
     * 根据 ID 获取工作区详情
     *
     * @param id 工作区 ID
     * @return 工作区实体
     */
    WorkspaceEntity getWorkspace(Long id);

    /**
     * 创建工作区
     *
     * @param entity         工作区实体
     * @param creatorUserId  创建者用户 ID
     * @return 创建后的工作区实体
     */
    WorkspaceEntity createWorkspace(WorkspaceEntity entity, Long creatorUserId);

    /**
     * 更新工作区
     *
     * @param entity 工作区实体（需包含 ID）
     * @return 更新后的工作区实体
     */
    WorkspaceEntity updateWorkspace(WorkspaceEntity entity);

    /**
     * 删除工作区
     *
     * @param id 工作区 ID
     */
    void deleteWorkspace(Long id);

    /**
     * 获取工作区成员列表（含用户名、昵称）
     *
     * @param workspaceId 工作区 ID
     * @return 成员列表
     */
    List<WorkspaceMemberEntity> listWorkspaceMembers(Long workspaceId);

    /**
     * 添加工作区成员
     * <p>
     * 若指定用户名的用户不存在，则使用密码创建账号后再加入工作区。
     *
     * @param workspaceId 工作区 ID
     * @param username    用户名
     * @param nickname    昵称（用户不存在时创建账号使用，可为 null）
     * @param password    密码（用户不存在时必填，已有用户忽略）
     * @param role        角色：admin / member / viewer
     * @return 成员实体
     */
    WorkspaceMemberEntity addWorkspaceMember(Long workspaceId, String username, String nickname,
                                              String password, String role);

    /**
     * 更新成员角色
     *
     * @param workspaceId 工作区 ID
     * @param userId      目标用户 ID
     * @param role        新角色：admin / member / viewer
     * @return 更新后的成员实体
     */
    WorkspaceMemberEntity updateWorkspaceMemberRole(Long workspaceId, Long userId, String role);

    /**
     * 移除工作区成员
     *
     * @param workspaceId 工作区 ID
     * @param userId      目标用户 ID
     */
    void removeWorkspaceMember(Long workspaceId, Long userId);

    /**
     * 根据用户名查询用户 ID
     *
     * @param username 用户名
     * @return 用户 ID，不存在返回 null
     */
    Long findUserIdByUsername(String username);
}
