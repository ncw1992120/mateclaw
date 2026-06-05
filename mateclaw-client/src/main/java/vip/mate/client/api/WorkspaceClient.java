package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.Workspace;
import vip.mate.client.model.WorkspaceMember;
import vip.mate.client.model.response.WorkspaceAccessResp;
import vip.mate.client.model.response.WorkspaceWithRoleResp;

import java.util.List;

/**
 * 工作区管理客户端
 */
public class WorkspaceClient extends AbstractApiClient {

    public WorkspaceClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取工作区列表
     */
    public R<List<WorkspaceWithRoleResp>> list() {
        return get(ApiPathConstants.WORKSPACE, new ParameterizedTypeReference<R<List<WorkspaceWithRoleResp>>>() {});
    }

    /**
     * 获取工作区详情
     */
    public R<Workspace> get(Long id) {
        return get(resolvePath(ApiPathConstants.WORKSPACE_BY_ID, id), new ParameterizedTypeReference<R<Workspace>>() {});
    }

    /**
     * 获取工作区访问权限
     */
    public R<WorkspaceAccessResp> getAccess(Long id) {
        return get(resolvePath(ApiPathConstants.WORKSPACE_ACCESS, id),
                new ParameterizedTypeReference<R<WorkspaceAccessResp>>() {});
    }

    /**
     * 创建工作区
     */
    public R<Workspace> create(Workspace entity) {
        return post(ApiPathConstants.WORKSPACE, entity, new ParameterizedTypeReference<R<Workspace>>() {});
    }

    /**
     * 更新工作区
     */
    public R<Workspace> update(Long id, Workspace entity) {
        return put(resolvePath(ApiPathConstants.WORKSPACE_BY_ID, id), entity,
                new ParameterizedTypeReference<R<Workspace>>() {});
    }

    /**
     * 删除工作区
     */
    public R<Void> delete(Long id) {
        return delete(resolvePath(ApiPathConstants.WORKSPACE_BY_ID, id), new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 获取工作区成员列表
     */
    public R<List<WorkspaceMember>> listMembers(Long id) {
        return get(resolvePath(ApiPathConstants.WORKSPACE_MEMBERS, id),
                new ParameterizedTypeReference<R<List<WorkspaceMember>>>() {});
    }

    /**
     * 添加工作区成员
     */
    public R<WorkspaceMember> addMember(Long id, WorkspaceMember body) {
        return post(resolvePath(ApiPathConstants.WORKSPACE_MEMBERS, id), body,
                new ParameterizedTypeReference<R<WorkspaceMember>>() {});
    }

    /**
     * 更新工作区成员角色
     */
    public R<WorkspaceMember> updateMemberRole(Long id, Long targetUserId, String role) {
        return put(resolvePath(ApiPathConstants.WORKSPACE_MEMBER_BY_ID, id, targetUserId), role,
                new ParameterizedTypeReference<R<WorkspaceMember>>() {});
    }

    /**
     * 移除工作区成员
     */
    public R<Void> removeMember(Long id, Long targetUserId) {
        return delete(resolvePath(ApiPathConstants.WORKSPACE_MEMBER_BY_ID, id, targetUserId),
                new ParameterizedTypeReference<R<Void>>() {});
    }
}
