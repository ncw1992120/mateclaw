package vip.mate.dataagent.auth.dto;

import lombok.Data;
import vip.mate.workspace.core.model.WorkspaceWithRoleVO;

import java.util.List;

/**
 * DataAgent 登录响应
 * <p>
 * 在 mateclaw-server 基础登录信息之上，附带当前用户可见的工作区列表，
 * 供前端登录后直接渲染工作区切换器，无需额外请求。
 */
@Data
public class DataAgentLoginResponse {

    /** 用户 ID */
    private Long id;

    /** JWT 令牌 */
    private String token;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 全局角色：admin / user */
    private String role;

    /** 当前用户可见的工作区列表（含成员角色） */
    private List<WorkspaceWithRoleVO> workspaces;
}
