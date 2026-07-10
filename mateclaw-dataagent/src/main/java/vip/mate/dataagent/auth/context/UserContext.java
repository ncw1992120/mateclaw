package vip.mate.dataagent.auth.context;

import vip.mate.dataagent.auth.interceptor.UserContextInterceptor;

/**
 * 当前请求的用户上下文信息
 * <p>
 * 由 {@link UserContextInterceptor} 在请求进入时填充，
 * 业务层通过 {@link UserContextHolder} 获取当前用户身份。
 */
public class UserContext {

    /** 用户 ID */
    private final Long userId;

    /** 用户名 */
    private final String username;

    /** 昵称 */
    private final String nickname;

    /** 全局角色：admin / user */
    private final String role;

    /** 当前请求的工作区 ID（来自请求头，缺省为默认工作区） */
    private final Long workspaceId;

    public UserContext(Long userId, String username, String nickname, String role, Long workspaceId) {
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.role = role;
        this.workspaceId = workspaceId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getNickname() {
        return nickname;
    }

    public String getRole() {
        return role;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    /**
     * 是否为全局管理员
     */
    public boolean isAdmin() {
        return role != null && role.equalsIgnoreCase("admin");
    }
}
