import api from './index'
import type { LogicalRelation, LogicalRelationCreateRequest, LogicalRelationUpdateRequest } from '@/types'

/** API 路径常量 */
const BASE_URL = '/dataagent/api/v1/logical-relations'

/** 查询逻辑外键关系列表 */
export function list(datasourceId: string, tableNames?: string) {
  return api.get<LogicalRelation[]>(BASE_URL, {
    params: { datasourceId, tableNames },
  })
}

/** 获取逻辑外键关系详情 */
export function get(id: string) {
  return api.get<LogicalRelation>(`${BASE_URL}/${id}`)
}

/** 创建逻辑外键关系 */
export function create(data: LogicalRelationCreateRequest) {
  return api.post<LogicalRelation>(BASE_URL, data)
}

/** 更新逻辑外键关系 */
export function update(id: string, data: LogicalRelationUpdateRequest) {
  return api.put<LogicalRelation>(`${BASE_URL}/${id}`, data)
}

/** 删除逻辑外键关系 */
export function remove(id: string) {
  return api.delete(`${BASE_URL}/${id}`)
}

/** 从物理外键自动初始化逻辑外键关系 */
export function autoInit(datasourceId: string) {
  return api.post<number>(`${BASE_URL}/auto-init`, null, {
    params: { datasourceId },
  })
}
