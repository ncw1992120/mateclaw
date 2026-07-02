import api from './index'

const BASE_URL = '/dataagent/api/v1/resource-grants'

/** 资源授权记录 */
export interface ResourceGrant {
  id: string
  resourceType: string
  resourceId: string
  workspaceId: string
  grantType: string
  granteeId: string
  permission: string
  grantedBy: string | null
  status: number
  expireTime: string | null
  createTime: string
  updateTime: string
  deleted: number
}

/** 授权请求参数 */
export interface ResourceGrantRequest {
  resourceType: string
  resourceId: string | number
  grantType: string
  granteeId: string
  permission: string
  expireTime?: string
}

/** 按资源查询授权列表 */
export function listGrantsByResource(resourceType: string, resourceId: string | number) {
  return api.get<ResourceGrant[]>(BASE_URL, {
    params: { resourceType, resourceId },
  })
}

/** 按被授权者查询授权列表 */
export function listGrantsByGrantee(
  grantType: string,
  granteeId: string,
  status?: number
) {
  return api.get<ResourceGrant[]>(`${BASE_URL}/grantee`, {
    params: { grantType, granteeId, status },
  })
}

/** 按工作区查询授权清单 */
export function listGrantsByWorkspace(resourceType?: string, status?: number) {
  return api.get<ResourceGrant[]>(`${BASE_URL}/workspace`, {
    params: { resourceType: resourceType || undefined, status },
  })
}

/** 授予权限 */
export function grantResource(data: ResourceGrantRequest) {
  return api.post<ResourceGrant>(BASE_URL, data)
}

/** 更新授权（权限、过期时间） */
export function updateGrant(id: string, data: { permission: string; expireTime?: string | null }) {
  return api.put<ResourceGrant>(`${BASE_URL}/${id}`, data)
}

/** 撤销授权 */
export function revokeGrant(id: string) {
  return api.delete(`${BASE_URL}/${id}`)
}

/** 检查权限 */
export function checkPermission(
  resourceType: string,
  resourceId: string | number,
  grantType: string,
  granteeId: string,
  permission: string
) {
  return api.get<boolean>(`${BASE_URL}/check`, {
    params: { resourceType, resourceId, grantType, granteeId, permission },
  })
}
