package vip.mate.dataagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.ResourceGrantRequest;
import vip.mate.dataagent.model.ResourceGrantEntity;
import vip.mate.dataagent.repository.ResourceGrantMapper;
import vip.mate.dataagent.service.ResourceGrantService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 通用资源授权服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceGrantServiceImpl implements ResourceGrantService {

    private final ResourceGrantMapper resourceGrantMapper;
    private final WorkspaceGuard workspaceGuard;

    @Override
    public List<ResourceGrantEntity> listGrantsByResource(Long workspaceId, String resourceType, Long resourceId) {
        return resourceGrantMapper.selectList(
                new LambdaQueryWrapper<ResourceGrantEntity>()
                        .eq(ResourceGrantEntity::getWorkspaceId, workspaceId)
                        .eq(ResourceGrantEntity::getResourceType, resourceType)
                        .eq(ResourceGrantEntity::getResourceId, resourceId)
                        .orderByDesc(ResourceGrantEntity::getCreateTime));
    }

    @Override
    public List<ResourceGrantEntity> listGrantsByGrantee(Long workspaceId, String grantType, String granteeId, Integer status) {
        LambdaQueryWrapper<ResourceGrantEntity> wrapper = new LambdaQueryWrapper<ResourceGrantEntity>()
                .eq(ResourceGrantEntity::getWorkspaceId, workspaceId)
                .eq(ResourceGrantEntity::getGrantType, grantType)
                .eq(ResourceGrantEntity::getGranteeId, granteeId)
                .orderByDesc(ResourceGrantEntity::getCreateTime);
        if (status != null) {
            wrapper.eq(ResourceGrantEntity::getStatus, status);
        }
        return resourceGrantMapper.selectList(wrapper);
    }

    @Override
    public List<ResourceGrantEntity> listGrantsByWorkspace(Long workspaceId, String resourceType, Integer status) {
        LambdaQueryWrapper<ResourceGrantEntity> wrapper = new LambdaQueryWrapper<ResourceGrantEntity>()
                .eq(ResourceGrantEntity::getWorkspaceId, workspaceId)
                .orderByDesc(ResourceGrantEntity::getCreateTime);
        if (resourceType != null && !resourceType.isBlank()) {
            wrapper.eq(ResourceGrantEntity::getResourceType, resourceType);
        }
        if (status != null) {
            wrapper.eq(ResourceGrantEntity::getStatus, status);
        }
        return resourceGrantMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public ResourceGrantEntity grant(ResourceGrantRequest request) {
        Long workspaceId = workspaceGuard.currentWorkspaceId();
        Long grantedBy = workspaceGuard.currentUserId();

        ResourceGrantEntity entity = new ResourceGrantEntity();
        entity.setResourceType(request.getResourceType());
        entity.setResourceId(request.getResourceId());
        entity.setWorkspaceId(workspaceId);
        entity.setGrantType(request.getGrantType());
        entity.setGranteeId(request.getGranteeId());
        entity.setPermission(request.getPermission());
        entity.setGrantedBy(grantedBy);
        entity.setStatus(DataAgentConstants.GRANT_STATUS_ACTIVE);
        if (request.getExpireTime() != null && !request.getExpireTime().isBlank()) {
            entity.setExpireTime(LocalDateTime.parse(request.getExpireTime(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        resourceGrantMapper.insert(entity);
        log.info("Resource granted: type={}, id={}, grantee={}, permission={}, by={}",
                request.getResourceType(), request.getResourceId(), request.getGranteeId(),
                request.getPermission(), grantedBy);
        return entity;
    }

    @Override
    @Transactional
    public void revoke(Long id) {
        int rows = resourceGrantMapper.update(null,
                new LambdaUpdateWrapper<ResourceGrantEntity>()
                        .eq(ResourceGrantEntity::getId, id)
                        .eq(ResourceGrantEntity::getStatus, DataAgentConstants.GRANT_STATUS_ACTIVE)
                        .set(ResourceGrantEntity::getStatus, DataAgentConstants.GRANT_STATUS_REVOKED));
        if (rows == 0) {
            throw new IllegalArgumentException("授权记录不存在或已撤销: " + id);
        }
        log.info("Resource grant revoked: id={}, by={}", id, workspaceGuard.currentUserId());
    }

    @Override
    public boolean checkPermission(Long workspaceId, String resourceType, Long resourceId,
                                   String grantType, String granteeId, String permission) {
        // 检查是否有过期授权
        Long count = resourceGrantMapper.selectCount(
                new LambdaQueryWrapper<ResourceGrantEntity>()
                        .eq(ResourceGrantEntity::getWorkspaceId, workspaceId)
                        .eq(ResourceGrantEntity::getResourceType, resourceType)
                        .eq(ResourceGrantEntity::getResourceId, resourceId)
                        .eq(ResourceGrantEntity::getGrantType, grantType)
                        .eq(ResourceGrantEntity::getGranteeId, granteeId)
                        .eq(ResourceGrantEntity::getPermission, permission)
                        .eq(ResourceGrantEntity::getStatus, DataAgentConstants.GRANT_STATUS_ACTIVE)
                        .and(w -> w.isNull(ResourceGrantEntity::getExpireTime)
                                .or()
                                .gt(ResourceGrantEntity::getExpireTime, LocalDateTime.now())));
        return count > 0;
    }
}
