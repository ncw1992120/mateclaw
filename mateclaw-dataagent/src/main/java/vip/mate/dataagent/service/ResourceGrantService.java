package vip.mate.dataagent.service;

import vip.mate.dataagent.dto.ResourceGrantRequest;
import vip.mate.dataagent.model.ResourceGrantEntity;

import java.util.List;

/**
 * 通用资源授权服务接口
 */
public interface ResourceGrantService {

    /**
     * 列出指定资源的授权记录
     *
     * @param workspaceId  工作区 ID
     * @param resourceType 资源类型
     * @param resourceId   资源 ID
     * @return 授权记录列表
     */
    List<ResourceGrantEntity> listGrantsByResource(Long workspaceId, String resourceType, Long resourceId);

    /**
     * 列出被授权者的授权记录
     *
     * @param workspaceId 工作区 ID
     * @param grantType   授权类型
     * @param granteeId   被授权者标识
     * @param status      状态过滤（null 表示不过滤）
     * @return 授权记录列表
     */
    List<ResourceGrantEntity> listGrantsByGrantee(Long workspaceId, String grantType, String granteeId, Integer status);

    /**
     * 列出当前工作区的所有授权记录
     *
     * @param workspaceId  工作区 ID
     * @param resourceType 资源类型过滤（null 表示不过滤）
     * @param status       状态过滤（null 表示不过滤）
     * @return 授权记录列表
     */
    List<ResourceGrantEntity> listGrantsByWorkspace(Long workspaceId, String resourceType, Integer status);

    /**
     * 授予权限
     *
     * @param request 授权请求
     * @return 创建后的授权记录
     */
    ResourceGrantEntity grant(ResourceGrantRequest request);

    /**
     * 撤销授权
     *
     * @param id 授权记录 ID
     */
    void revoke(Long id);

    /**
     * 检查权限
     *
     * @param workspaceId  工作区 ID
     * @param resourceType 资源类型
     * @param resourceId   资源 ID
     * @param grantType    授权类型
     * @param granteeId    被授权者标识
     * @param permission   权限
     * @return true 如果有权限
     */
    boolean checkPermission(Long workspaceId, String resourceType, Long resourceId,
                            String grantType, String granteeId, String permission);
}
