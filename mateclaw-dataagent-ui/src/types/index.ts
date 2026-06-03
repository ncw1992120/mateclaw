/** Agent 实体 */
export interface Agent {
  id: number
  name: string
  description: string
  agentType: string
  systemPrompt: string
  modelName: string
  maxIterations: number
  enabled: boolean
  icon: string
  tags: string
  workspaceId: number
  defaultThinkingLevel: string
  createTime: string
  updateTime: string
}

/** 数据源实体 */
export interface Datasource {
  id: string
  name: string
  description: string
  sourceType: string
  host: string
  port: number
  databaseName: string
  username: string
  password: string
  connectionParams: string
  schemaName: string
  enabled: boolean
  lastTestTime: string
  lastTestOk: boolean
  schemaStatus: string
  lastSchemaDiscoveryTime: string
  tableCount: number
  createTime: string
  updateTime: string
}

/** 数据源表元数据 */
export interface DatasourceTable {
  id: string
  datasourceId: string
  tableName: string
  tableComment: string
  tableType: string
  rowCount: number
  dataSizeBytes: number
  schemaName: string
  engine: string
  columnCount: number
  columns: DatasourceColumn[]
  createTime: string
  updateTime: string
}

/** 数据源字段元数据 */
export interface DatasourceColumn {
  id: string
  datasourceId: string
  tableId: string
  columnName: string
  columnComment: string
  dataType: string
  columnSize: number
  decimalDigits: number
  nullable: boolean
  primaryKey: boolean
  indexed: boolean
  defaultValue: string
  ordinalPosition: number
  foreignKeyTable: string
  foreignKeyColumn: string
  createTime: string
  updateTime: string
}

/** 数据源类型选项 */
export const SOURCE_TYPE_OPTIONS = [
  { value: 'mysql', label: 'MySQL', group: 'relational' },
  { value: 'postgresql', label: 'PostgreSQL', group: 'relational' },
  { value: 'oracle', label: 'Oracle', group: 'relational' },
  { value: 'snowflake', label: 'Snowflake', group: 'warehouse' },
  { value: 'bigquery', label: 'BigQuery', group: 'warehouse' },
  { value: 'redshift', label: 'Redshift', group: 'warehouse' },
  { value: 'clickhouse', label: 'ClickHouse', group: 'olap' },
  { value: 'doris', label: 'Doris', group: 'olap' },
  { value: 'mongodb', label: 'MongoDB', group: 'nosql' },
  { value: 'elasticsearch', label: 'Elasticsearch', group: 'nosql' },
  { value: 'csv', label: 'CSV', group: 'file' },
  { value: 'excel', label: 'Excel', group: 'file' },
  { value: 'parquet', label: 'Parquet', group: 'file' },
  { value: 'api', label: 'API', group: 'api' },
  { value: 'kafka', label: 'Kafka', group: 'mq' },
] as const

/** 数据源类型分组 */
export const SOURCE_TYPE_GROUPS = [
  { value: 'relational', label: '关系型数据库' },
  { value: 'warehouse', label: '数据仓库' },
  { value: 'olap', label: 'OLAP 引擎' },
  { value: 'nosql', label: 'NoSQL' },
  { value: 'file', label: '文件系统' },
  { value: 'api', label: 'API 接口' },
  { value: 'mq', label: '消息队列' },
] as const

/** 模型配置实体 */
export interface ModelConfig {
  id: number
  name: string
  provider: string
  modelName: string
  description: string
  temperature: number
  maxTokens: number
  maxInputTokens: number
  requestTimeoutSeconds: number
  topP: number
  enableSearch: boolean
  searchStrategy: string
  builtin: boolean
  enabled: boolean
  isDefault: boolean
  modelType: string
  modalities: string
  createTime: string
  updateTime: string
}

/** 模型供应商实体 */
export interface ModelProvider {
  providerId: string
  name: string
  apiKeyPrefix: string
  chatModel: string
  apiKey: string
  baseUrl: string
  generateKwargs: string
  isCustom: boolean
  isLocal: boolean
  supportModelDiscovery: boolean
  supportConnectionCheck: boolean
  freezeUrl: boolean
  requireApiKey: boolean
  authType: string
  fallbackPriority: number
  enabled: boolean
  models?: ModelInfo[]
  extraModels?: ModelInfo[]
}

/** 模型简要信息（Provider 下挂） */
export interface ModelInfo {
  id: number
  name: string
  modelName: string
  enabled: boolean
  isDefault: boolean
  modelType: string
  description: string
}

/** 聊天消息角色 */
export type ChatRole = 'user' | 'assistant'

/** 聊天富内容卡片类型 */
export type ChatCardType = 'text' | 'queryplan' | 'insight' | 'chart' | 'clarify' | 'dashboard' | 'followup' | 'feedback'

/** QueryPlan 卡片数据 */
export interface QueryPlanData {
  indicator: string
  dimension: string
  time: string
  compare: string
  sort: string
  limit: string
  [key: string]: string
}

/** 图表卡片数据 */
export interface ChartCardData {
  title: string
  xData: string[]
  series: ChartSeriesItem[]
}

/** 图表系列项 */
export interface ChartSeriesItem {
  name: string
  data: number[]
  type?: string
}

/** 澄清卡片数据 */
export interface ClarifyOption {
  label: string
  recommend?: boolean
}

export interface ClarifyData {
  title: string
  desc: string
  options: ClarifyOption[]
}

/** 仪表盘预览卡片数据 */
export interface DashboardKpi {
  name: string
  val: string
  chg: string
  up: boolean
}

export interface DashboardCardData {
  kpis: DashboardKpi[]
}

/** 追问建议卡片数据 */
export type FollowupData = string[]

/** 聊天消息富内容卡片 */
export interface ChatCard {
  type: ChatCardType
  data: QueryPlanData | ChartCardData | ClarifyData | DashboardCardData | FollowupData | string
}

/** 聊天消息 */
export interface ChatMessage {
  role: ChatRole
  content: string
  thinking?: string
  cards?: ChatCard[]
  timestamp: number
  /** 后端持久化的 Agent 元数据：toolCalls、segments 等 */
  metadata?: Record<string, unknown>
}

/** SSE 结构化事件 */
export interface SseEvent {
  event: string
  data: Record<string, unknown> | string
  /** Server-assigned monotonic id for reconnect dedup */
  id?: string
}

/** 通用后端响应结构 */
export interface R<T> {
  code: number
  msg: string
  data: T
}

/** 聊天请求参数 */
export interface ChatRequest {
  agentId: number
  message: string
  conversationId: string
  /** 模型名称（可选，覆盖 Agent 默认模型） */
  modelName?: string
}

/** 会话摘要 */
export interface Conversation {
  id: number
  conversationId: string
  title: string
  agentId: number
  messageCount: number
  lastMessage: string
  lastActiveTime: string
  createTime: string
  updateTime: string
}

/** 消息视图对象 */
export interface MessageVO {
  id: number
  conversationId: string
  role: string
  content: string
  toolName?: string
  status?: string
  /** Agent 事件元数据：toolCalls, segments 等 */
  metadata?: Record<string, unknown>
  /** Prompt tokens 消耗 */
  promptTokens?: number | null
  /** Completion tokens 消耗 */
  completionTokens?: number | null
  /** 实际使用的模型名称 */
  runtimeModel?: string | null
  /** 实际使用的模型 Provider ID */
  runtimeProvider?: string | null
  contentParts?: unknown[] | null
  createTime: string
}

/** 技能构建器主题配置 */
export interface SkillTheme {
  label: string
  indicators: string[]
  dimensions: string[]
  template: string
}

/** 时间范围预设项 */
export interface TimePreset {
  key: string
  label: string
}

/** Agent 类型枚举 */
export const AGENT_TYPES = [
  { value: 'react', label: 'ReAct' },
  { value: 'plan_execute', label: 'Plan-Execute' },
] as const

/** 思考深度枚举 */
export const THINKING_LEVELS = [
  { value: 'off', label: '关闭' },
  { value: 'low', label: '低' },
  { value: 'medium', label: '中' },
  { value: 'high', label: '高' },
  { value: 'max', label: '最高' },
] as const

/** 数据集实体 */
export interface Dataset {
  id: string
  name: string
  description: string
  datasourceId: string
  datasourceName: string
  tableIds: string
  tableNames: string
  status: string
  rowCount: number
  columnCount: number
  owner: string
  modifier: string
  createTime: string
  updateTime: string
  fields?: DatasetField[]
}

/** 数据集字段实体 */
export interface DatasetField {
  id: string
  datasetId: string
  columnName: string
  columnAlias: string
  columnComment: string
  dataType: string
  columnSize: number
  decimalDigits: number
  fieldCategory: string
  primaryKey: boolean
  nullable: boolean
  defaultValue: string
  ordinalPosition: number
  datasourceId: string
  sourceTableId: string
  sourceTableName: string
  createTime: string
  updateTime: string
}

/** 数据集列定义 */
export interface DatasetColumnDef {
  name: string
  title: string
  dataType: string
  fieldCategory: string
  editable: boolean
  width: number
}

/** 数据集数据视图 */
export interface DatasetData {
  columns: DatasetColumnDef[]
  rows: Record<string, unknown>[]
  total: number
}

/** 模型类型枚举 */
export const MODEL_TYPES = [
  { value: 'chat', label: '对话' },
  { value: 'embedding', label: '向量' },
] as const
