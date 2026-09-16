import type { Dataset, Datasource } from '@/types'

export type DatasourceCategory = 'aloudata' | 'jdbc' | 'api' | 'file' | 'unknown'

export interface SourceGroup<T> {
  category: DatasourceCategory
  label: string
  items: T[]
}

export type DatasetGroup = SourceGroup<Dataset> & { datasets: Dataset[] }

/** 将后端连接类型归一为 UI 数据源大类。未知类型不猜测为 JDBC。 */
export function classifyDatasourceType(sourceType?: string): DatasourceCategory {
  const normalized = String(sourceType || '').trim().toLowerCase().replace(/[-\s]/g, '_')
  if (normalized.includes('aloudata') || normalized.includes('analysis_view')) return 'aloudata'
  if (['api', 'http', 'http_api', 'rest'].includes(normalized) || normalized.includes('http_api')) return 'api'
  if (['file', 'excel', 'csv', 'txt', 'json', 'parquet', 'object_storage'].includes(normalized)) return 'file'
  if (
    normalized === 'jdbc'
    || normalized === 'jdbc_table'
    || normalized === 'jdbc_sql'
    || normalized.includes('mysql')
    || normalized.includes('postgres')
    || normalized.includes('doris')
    || normalized.includes('starrocks')
    || normalized.includes('oracle')
    || normalized.includes('sqlserver')
    || normalized.includes('clickhouse')
  ) return 'jdbc'
  return 'unknown'
}

export function datasetCategoryLabel(category: DatasourceCategory): string {
  return {
    aloudata: 'Aloudata',
    jdbc: 'JDBC',
    api: '接口',
    file: '文件',
    unknown: '其他',
  }[category]
}

/** 按固定产品顺序分组，不改变每个分组内的后端返回顺序。 */
export function groupDatasets(datasets: Dataset[]): DatasetGroup[] {
  return groupSourceItems(datasets).map(group => ({ ...group, datasets: group.items }))
}

export function groupDatasources(datasources: Datasource[]): SourceGroup<Datasource>[] {
  return groupSourceItems(datasources)
}

function groupSourceItems<T extends { sourceType?: string }>(items: T[]): SourceGroup<T>[] {
  const order: DatasourceCategory[] = ['aloudata', 'jdbc', 'api', 'file', 'unknown']
  const grouped = new Map<DatasourceCategory, T[]>()
  items.forEach((item) => {
    const category = classifyDatasourceType(item.sourceType)
    const list = grouped.get(category) ?? []
    list.push(item)
    grouped.set(category, list)
  })
  return order
    .filter(category => grouped.has(category))
    .map(category => ({ category, label: datasetCategoryLabel(category), items: grouped.get(category)! }))
}
