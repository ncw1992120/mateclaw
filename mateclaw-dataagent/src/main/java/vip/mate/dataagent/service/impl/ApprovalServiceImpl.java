package vip.mate.dataagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.ApprovalProcessRequest;
import vip.mate.dataagent.dto.ApprovalSubmitRequest;
import vip.mate.dataagent.model.ApprovalRecordEntity;
import vip.mate.dataagent.repository.ApprovalRecordMapper;
import vip.mate.dataagent.service.ApprovalService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批流程服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalServiceImpl implements ApprovalService {

    private final ApprovalRecordMapper approvalRecordMapper;
    private final WorkspaceGuard workspaceGuard;

    @Override
    @Transactional
    public ApprovalRecordEntity submit(ApprovalSubmitRequest request) {
        Long workspaceId = workspaceGuard.currentWorkspaceId();
        Long requesterId = workspaceGuard.currentUserId();

        ApprovalRecordEntity entity = new ApprovalRecordEntity();
        entity.setApprovalType(request.getApprovalType());
        entity.setResourceType(request.getResourceType());
        entity.setResourceId(request.getResourceId());
        entity.setResourceName(request.getResourceName());
        entity.setWorkspaceId(workspaceId);
        entity.setRequesterId(requesterId);
        entity.setAction(request.getAction());
        entity.setPayloadJson(request.getPayloadJson());
        entity.setStatus(DataAgentConstants.APPROVAL_STATUS_PENDING);
        entity.setCurrentStep(0);
        entity.setSubmittedAt(LocalDateTime.now());
        approvalRecordMapper.insert(entity);
        log.info("Approval submitted: type={}, resource={}/{}, requester={}",
                request.getApprovalType(), request.getResourceType(), request.getResourceId(), requesterId);
        return entity;
    }

    @Override
    @Transactional
    public void approve(Long id, ApprovalProcessRequest request) {
        ApprovalRecordEntity entity = getApprovalEntity(id);
        if (!DataAgentConstants.APPROVAL_STATUS_PENDING.equals(entity.getStatus())) {
            throw new IllegalStateException("审批记录状态不允许操作: " + entity.getStatus());
        }
        Long approverId = workspaceGuard.currentUserId();
        entity.setStatus(DataAgentConstants.APPROVAL_STATUS_APPROVED);
        entity.setApproverId(approverId);
        entity.setComment(request != null ? request.getComment() : null);
        entity.setApprovedAt(LocalDateTime.now());
        approvalRecordMapper.updateById(entity);
        log.info("Approval approved: id={}, approver={}", id, approverId);
    }

    @Override
    @Transactional
    public void reject(Long id, ApprovalProcessRequest request) {
        ApprovalRecordEntity entity = getApprovalEntity(id);
        if (!DataAgentConstants.APPROVAL_STATUS_PENDING.equals(entity.getStatus())) {
            throw new IllegalStateException("审批记录状态不允许操作: " + entity.getStatus());
        }
        Long approverId = workspaceGuard.currentUserId();
        entity.setStatus(DataAgentConstants.APPROVAL_STATUS_REJECTED);
        entity.setApproverId(approverId);
        entity.setComment(request != null ? request.getComment() : null);
        entity.setApprovedAt(LocalDateTime.now());
        approvalRecordMapper.updateById(entity);
        log.info("Approval rejected: id={}, approver={}", id, approverId);
    }

    @Override
    @Transactional
    public void cancel(Long id) {
        ApprovalRecordEntity entity = getApprovalEntity(id);
        if (!DataAgentConstants.APPROVAL_STATUS_PENDING.equals(entity.getStatus())) {
            throw new IllegalStateException("审批记录状态不允许撤回: " + entity.getStatus());
        }
        Long currentUserId = workspaceGuard.currentUserId();
        if (!entity.getRequesterId().equals(currentUserId) && !workspaceGuard.isCurrentAdmin()) {
            throw new IllegalStateException("仅申请人或管理员可撤回审批");
        }
        entity.setStatus(DataAgentConstants.APPROVAL_STATUS_CANCELLED);
        approvalRecordMapper.updateById(entity);
        log.info("Approval cancelled: id={}, by={}", id, currentUserId);
    }

    @Override
    public List<ApprovalRecordEntity> listApprovals(Long workspaceId, String status, String resourceType) {
        LambdaQueryWrapper<ApprovalRecordEntity> wrapper = new LambdaQueryWrapper<ApprovalRecordEntity>()
                .eq(ApprovalRecordEntity::getWorkspaceId, workspaceId)
                .orderByDesc(ApprovalRecordEntity::getSubmittedAt);
        if (status != null && !status.isBlank()) {
            wrapper.eq(ApprovalRecordEntity::getStatus, status);
        }
        if (resourceType != null && !resourceType.isBlank()) {
            wrapper.eq(ApprovalRecordEntity::getResourceType, resourceType);
        }
        return approvalRecordMapper.selectList(wrapper);
    }

    @Override
    public ApprovalRecordEntity getApproval(Long id) {
        return getApprovalEntity(id);
    }

    /**
     * 获取审批记录实体（不存在则抛异常）
     */
    private ApprovalRecordEntity getApprovalEntity(Long id) {
        ApprovalRecordEntity entity = approvalRecordMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("审批记录不存在: " + id);
        }
        return entity;
    }
}
