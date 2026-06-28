package vip.mate.dataagent.service;

import vip.mate.dataagent.dto.ApprovalProcessRequest;
import vip.mate.dataagent.dto.ApprovalSubmitRequest;
import vip.mate.dataagent.model.ApprovalRecordEntity;

import java.util.List;

/**
 * 审批流程服务接口
 */
public interface ApprovalService {

    /**
     * 提交审批申请
     *
     * @param request 提交请求
     * @return 创建后的审批记录
     */
    ApprovalRecordEntity submit(ApprovalSubmitRequest request);

    /**
     * 通过审批
     *
     * @param id      审批记录 ID
     * @param request 审批处理请求（含审批意见）
     */
    void approve(Long id, ApprovalProcessRequest request);

    /**
     * 拒绝审批
     *
     * @param id      审批记录 ID
     * @param request 审批处理请求（含审批意见）
     */
    void reject(Long id, ApprovalProcessRequest request);

    /**
     * 撤回审批（仅申请人可操作）
     *
     * @param id 审批记录 ID
     */
    void cancel(Long id);

    /**
     * 列出审批记录
     *
     * @param workspaceId  工作区 ID
     * @param status       状态过滤（null 表示不过滤）
     * @param resourceType 资源类型过滤（null 表示不过滤）
     * @return 审批记录列表
     */
    List<ApprovalRecordEntity> listApprovals(Long workspaceId, String status, String resourceType);

    /**
     * 获取审批详情
     *
     * @param id 审批记录 ID
     * @return 审批记录
     */
    ApprovalRecordEntity getApproval(Long id);
}
