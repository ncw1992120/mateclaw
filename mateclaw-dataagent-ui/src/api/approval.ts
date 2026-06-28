import api from './index'

const BASE_URL = '/dataagent/api/v1/approvals'

/** 审批记录 */
export interface ApprovalRecord {
  id: string
  approvalType: string
  resourceType: string
  resourceId: string
  resourceName: string | null
  workspaceId: string
  requesterId: string
  requesterName: string | null
  action: string
  payloadJson: string | null
  status: string
  currentStep: number
  approverId: string | null
  approverName: string | null
  comment: string | null
  submittedAt: string
  approvedAt: string | null
  createTime: string
  updateTime: string
  deleted: number
}

/** 提交审批请求参数 */
export interface ApprovalSubmitRequest {
  approvalType: string
  resourceType: string
  resourceId: string | number
  resourceName?: string
  action: string
  payloadJson?: string
}

/** 审批处理请求参数 */
export interface ApprovalProcessRequest {
  comment?: string
}

/** 审批状态常量 */
export const APPROVAL_STATUS = {
  PENDING: 'pending',
  APPROVED: 'approved',
  REJECTED: 'rejected',
  CANCELLED: 'cancelled',
} as const

/** 提交审批申请 */
export function submitApproval(data: ApprovalSubmitRequest) {
  return api.post<ApprovalRecord>(BASE_URL, data)
}

/** 通过审批 */
export function approveApproval(id: string, data?: ApprovalProcessRequest) {
  return api.put(`${BASE_URL}/${id}/approve`, data || {})
}

/** 拒绝审批 */
export function rejectApproval(id: string, data?: ApprovalProcessRequest) {
  return api.put(`${BASE_URL}/${id}/reject`, data || {})
}

/** 撤回审批 */
export function cancelApproval(id: string) {
  return api.put(`${BASE_URL}/${id}/cancel`, {})
}

/** 审批记录列表 */
export function listApprovals(status?: string, resourceType?: string) {
  return api.get<ApprovalRecord[]>(BASE_URL, {
    params: { status, resourceType },
  })
}

/** 审批详情 */
export function getApproval(id: string) {
  return api.get<ApprovalRecord>(`${BASE_URL}/${id}`)
}
