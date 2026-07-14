package vip.mate.dataagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.InsightDashboardCreateRequest;
import vip.mate.dataagent.dto.InsightDashboardUpdateRequest;
import vip.mate.dataagent.dto.InsightDashboardVO;
import vip.mate.dataagent.exception.BusinessException;
import vip.mate.dataagent.model.InsightDashboardEntity;
import vip.mate.dataagent.repository.InsightDashboardMapper;
import vip.mate.dataagent.service.InsightDashboardService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 洞察仪表盘服务实现
 * <p>
 * 按工作区隔离仪表盘资源，CRUD 操作均校验归属权限。
 */
@Service
@RequiredArgsConstructor
public class InsightDashboardServiceImpl implements InsightDashboardService {

    private final InsightDashboardMapper insightDashboardMapper;
    private final WorkspaceGuard workspaceGuard;

    @Override
    public List<InsightDashboardVO> listDashboards() {
        LambdaQueryWrapper<InsightDashboardEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InsightDashboardEntity::getDeleted, 0);
        wrapper.eq(InsightDashboardEntity::getWorkspaceId, workspaceGuard.currentWorkspaceId());
        wrapper.orderByDesc(InsightDashboardEntity::getUpdateTime);
        List<InsightDashboardEntity> entities = insightDashboardMapper.selectList(wrapper);
        return entities.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public InsightDashboardVO getDashboard(Long id) {
        requireOwnership(id);
        InsightDashboardEntity entity = insightDashboardMapper.selectById(id);
        return toVO(entity);
    }

    @Override
    public InsightDashboardVO createDashboard(InsightDashboardCreateRequest request) {
        InsightDashboardEntity entity = new InsightDashboardEntity();
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setSchemaJson(request.getSchemaJson() != null ? request.getSchemaJson() : "{\"version\":\"1.0\",\"components\":[]}");
        entity.setStatus(DataAgentConstants.INSIGHT_DASHBOARD_STATUS_DRAFT);
        entity.setAgentId(request.getAgentId());
        entity.setWorkspaceId(workspaceGuard.currentWorkspaceId());
        entity.setOwnerId(workspaceGuard.currentUserId());
        entity.setOwnerName(request.getOwnerName() != null ? request.getOwnerName() : workspaceGuard.currentUserNickname());
        entity.setDeleted(0);
        insightDashboardMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    public InsightDashboardVO updateDashboard(Long id, InsightDashboardUpdateRequest request) {
        requireOwnership(id);
        InsightDashboardEntity entity = insightDashboardMapper.selectById(id);
        if (request.getName() != null) {
            entity.setName(request.getName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getSchemaJson() != null) {
            entity.setSchemaJson(request.getSchemaJson());
        }
        if (request.getReportContent() != null) {
            entity.setReportContent(request.getReportContent());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        if (request.getAgentId() != null) {
            entity.setAgentId(request.getAgentId());
        }
        if (request.getOwnerName() != null) {
            entity.setOwnerName(request.getOwnerName());
        }
        entity.setModifier(workspaceGuard.currentUserNickname());
        insightDashboardMapper.updateById(entity);
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDashboard(Long id) {
        requireOwnership(id);
        InsightDashboardEntity entity = insightDashboardMapper.selectById(id);
        entity.setDeleted(1);
        insightDashboardMapper.updateById(entity);
    }

    private void requireOwnership(Long id) {
        InsightDashboardEntity entity = insightDashboardMapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(404, "仪表盘不存在: " + id);
        }
        Long currentWorkspaceId = workspaceGuard.currentWorkspaceId();
        if (entity.getWorkspaceId() == null
                || !entity.getWorkspaceId().equals(currentWorkspaceId)) {
            throw new BusinessException(403, "无权访问该仪表盘");
        }
    }

    private InsightDashboardVO toVO(InsightDashboardEntity entity) {
        InsightDashboardVO vo = new InsightDashboardVO();
        BeanUtils.copyProperties(entity, vo);
        if (entity.getCreateTime() != null) {
            vo.setCreateTime(entity.getCreateTime().toString());
        }
        if (entity.getUpdateTime() != null) {
            vo.setUpdateTime(entity.getUpdateTime().toString());
        }
        return vo;
    }
}
