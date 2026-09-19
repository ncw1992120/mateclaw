/** 工作区（含成员角色） */
export interface Workspace {
  id: number | string
  name: string
  slug: string
  description: string
  basePath: string | null
  ownerId: number | string | null
  settingsJson: string | null
  createTime: string
  updateTime: string
  /** 用户在该工作区的成员角色 */
  memberRole: string | null
  /** 角色级别（owner=4..viewer=1，0 表示非成员） */
  roleLevel: number
  /** 是否为全局管理员 */
  isGlobalAdmin: boolean
  /** 生效角色（全局管理员为 owner，否则为 memberRole） */
  effectiveRole: string
}

/** 工作区成员 */
export interface WorkspaceMember {
  id: number | string
  workspaceId: number | string
  userId: number | string
  username: string
  nickname: string
  role: string
  createTime: string
  updateTime: string
}

/** 登录响应 */
export interface LoginResponse {
  id: number | string
  token: string
  username: string
  nickname: string
  role: string
  /** 可见工作区列表 */
  workspaces: Workspace[]
}

/** 当前用户信息（刷新恢复，token 可能为 null） */
export interface CurrentUserInfo {
  id: number | string
  token: string | null
  username: string
  nickname: string
  role: string
  workspaces: Workspace[]
}

/** Agent 实体 */
export interface Agent {
  id: number | string
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
  /** Agent 主知识库 ID（用于 wiki 工具默认目标） */
  primaryKbId?: number | string | null
  /** 是否禁用全部技能（true 时不注入 SKILL.md，且禁用技能扩展工具） */
  skillsDisabled?: boolean
  /** 是否禁用全部用户可选工具 */
  toolsDisabled?: boolean
  createTime: string
  updateTime: string
}

/** 工作区文件实体（智能体上下文文件） */
export interface WorkspaceFile {
  id: number | string
  agentId: number | string
  filename: string
  content: string | null
  fileSize: number
  enabled: boolean
  sortOrder: number
  ownerKey: string | null
  scope: string
  createTime: string
  updateTime: string
}

/** Agent 已绑定的技能 */
export interface AgentSkillBinding {
  id: number | string
  agentId: number | string
  skillId: number
  enabled: boolean
  createTime: string
  updateTime: string
}

/** Agent 已绑定的工具 */
export interface AgentToolBinding {
  id: number | string
  agentId: number | string
  toolName: string
  enabled: boolean
  createTime: string
  updateTime: string
}

/** Agent 偏好 Provider 配置 */
export interface AgentProviderPreference {
  id: number | string
  agentId: number | string
  providerId: string
  sortOrder: number
  enabled: boolean
  createTime: string
  updateTime: string
}

/** 可绑定工具（Picker 用） */
export interface AvailableTool {
  rowId: string
  source: string
  providerId: number | null
  providerName: string | null
  name: string
  rawName: string
  description: string
  group: string
  groupId: string
  stale: boolean
  available: boolean
  unavailableReason: string | null
}

/** 可绑定知识库（Picker 用） */
export interface AvailableKnowledgeBase {
  id: number | string
  name: string
  description: string
  pageCount?: number
  rawCount?: number
}

/** 数据源实体 */
export interface Datasource {
  id: string
  name: string
  description: string
  sourceType: string
  host: string
  /** 产品层服务地址（Aloudata anymetrics，端口默认 8083） */
  productHost: string
  /** 语义层服务地址（Aloudata semantic，端口默认 8085） */
  semanticHost: string
  port: number
  databaseName: string
  username: string
  password: string
  connectionParams: string
  schemaName: string
  /** 元数据是否共享（同工作区所有用户可查看） */
  metaShared: boolean
  enabled: boolean
  /** Aloudata 语义层定时同步开关 */
  aloudataSyncEnabled?: boolean
  /** Aloudata 语义层定时同步 cron 表达式（Spring 6 段） */
  aloudataSyncCron?: string
  /** 最近一次 Aloudata 语义层同步完成时间 */
  lastAloudataSyncTime?: string
  lastTestTime: string
  lastTestOk: boolean
  schemaStatus: string
  lastSchemaDiscoveryTime: string
  tableCount: number
  /** 当前用户对该数据源的最高权限：view / use / edit */
  permission?: string
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

/** Aloudata 同步状态 */
export interface AloudataSyncStatus {
  metricCount: number
  dimensionCount: number
  metricDimensionCount: number
  categoryCount: number
  elapsedMs: number
  status: 'completed' | 'not_synced' | 'failed' | 'running'
  message: string
}

/** Aloudata 已同步指标 */
export interface AloudataSyncedMetric {
  metricName: string
  metricDisplayName: string
  type: string
  businessCaliber: string
  synonyms: string[]
  metricCategoryName: string
  unit: string
  owner?: string
  status?: string
  availableDimensions: string[]
}

/** Aloudata 已同步维度 */
export interface AloudataSyncedDimension {
  dimName: string
  dimDisplayName: string
  originDataType: string
  dimDescription: string
  synonyms: string[]
  configType: string
  isTimeDimension: boolean
  exampleValues: string
  datasetName?: string
}

/** Aloudata 指标分页结果 */
export interface AloudataMetricPage {
  records: AloudataSyncedMetric[]
  total: number
  size: number
  current: number
  pages: number
}

/** Aloudata 维度分页结果 */
export interface AloudataDimensionPage {
  records: AloudataSyncedDimension[]
  total: number
  size: number
  current: number
  pages: number
}

/** Aloudata 类目数量统计 */
export interface AloudataCategoryCount {
  categoryId: string
  categoryName: string
  parentId?: string
  count: number
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
export type ChatCardType = 'text' | 'queryplan' | 'insight' | 'chart' | 'echarts' | 'clarify' | 'dashboard' | 'followup' | 'feedback' | 'recommended_questions'

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

/**
 * ECharts 标准 Option 数据（后端直接返回的 ECharts 配置）
 * 后端返回符合 ECharts option 规范的数据时，前端直接透传给 echarts.setOption() 渲染
 */
export interface EChartsOptionData {
  /** 图表标题 */
  title?: string
  /** ECharts 标准 option 对象 */
  option: Record<string, unknown>
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

/** 推荐问题卡片数据 */
export interface RecommendedQuestionData {
  /** 推荐问题列表 */
  questions: string[]
}

/** 聊天消息富内容卡片 */
export interface ChatCard {
  type: ChatCardType
  data: QueryPlanData | ChartCardData | EChartsOptionData | ClarifyData | DashboardCardData | FollowupData | RecommendedQuestionData | string
}

/** 聊天附件 */
export interface ChatAttachment {
  fileName: string
  storedName: string
  url: string
  /** 服务端本地路径，用于后端工具消费 */
  path: string
  size: number
  contentType: string
}

/** 聊天消息状态 */
export type ChatMessageStatus = 'streaming' | 'completed' | 'failed' | 'stopped'

/** 计划执行进度元数据（Plan-Execute 模式） */
export interface PlanMeta {
  planId: string | number
  steps: string[]
  currentStep: number
  stepResults?: { result: string; status: 'completed' | 'failed' }[]
  /** 计划整体状态：running / completed / failed */
  planStatus?: 'running' | 'completed' | 'failed'
}

/** 委派子 agent 执行的工具条目（delegation_progress 累积） */
export interface DelegationToolEntry {
  name: string
  status: 'running' | 'completed' | 'error'
}

/**
 * 委派调用树中的单个子 agent 节点（depth >= 2）。
 * depth-1 的子 agent 复用 MessageSegment（type='tool_call'），其 childTimeline.children 即此节点列表。
 * 由 delegation_* 事件流按 subagentId/parentSubagentId 重建。
 */
export interface DelegationNode {
  subagentId: string
  agentName: string
  status: 'running' | 'completed' | 'error'
  depth: number
  task?: string
  result?: string
  durationMs?: number
  /** 心跳看门狗标记：子 agent 长时间无进展 */
  stale?: boolean
  /** 异步委派（delegateAsync）：父 agent 不阻塞，结果通过 task_output 取回 */
  async?: boolean
  plan?: PlanMeta
  tools?: DelegationToolEntry[]
  children?: DelegationNode[]
}

/**
 * depth-1 委派 segment 携带的子 agent 时间线容器。
 * 挂在 MessageSegment.childTimeline 上，记录该子 agent 自身的 plan/tools/嵌套子节点。
 */
export interface DelegationTimeline {
  tools?: DelegationToolEntry[]
  children?: DelegationNode[]
  plan?: PlanMeta
}

/** 聊天消息 */
export interface ChatMessage {
  role: ChatRole
  content: string
  cards?: ChatCard[]
  timestamp: number
  /** 后端持久化的 Agent 元数据：toolCalls、segments 等 */
  metadata?: Record<string, unknown>
  /** 附件列表 */
  attachments?: ChatAttachment[]
  /** 消息状态：streaming(生成中) / completed(完成) / failed(失败) / stopped(已停止) */
  status?: ChatMessageStatus
  /** 错误信息（status=failed 时存在） */
  errorInfo?: import('./chatError').ChatErrorInfo
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

/** 上下文使用分类 */
export interface ContextUsageCategory {
  name: string
  label: string
  tokens: number
  color?: string
}

/** 上下文压缩状态 */
export interface ContextCompressionStatus {
  status: 'none' | 'compacted' | 'failed'
  preTokens?: number | null
  postTokens?: number | null
  messagesSummarized?: number | null
  tailKept?: number | null
  summaryId?: number | null
}

/** 上下文使用情况 */
export interface ContextUsage {
  contextWindow: number
  usedTokens: number
  usedPercent: number
  categories: ContextUsageCategory[]
  compression: ContextCompressionStatus
  conversationId: string
  timestamp: number
}

/** 聊天请求参数 */
export interface ChatRequest {
  agentId: number | string
  message: string
  conversationId: string
  /** 模型名称（可选，覆盖 Agent 默认模型） */
  modelName?: string
  /** 数据源 ID 白名单（可选，限制 LLM 只能访问指定数据源） */
  datasourceIds?: string[]
}

/** 会话摘要 */
export interface Conversation {
  id: number | string
  conversationId: string
  title: string
  agentId: number | string
  messageCount: number
  lastMessage: string
  lastActiveTime: string
  pinned?: number
  /** 会话绑定的模型 Provider ID（null 表示继承 Agent 默认） */
  modelProvider?: string | null
  /** 会话绑定的模型名称（null 表示继承 Agent 默认） */
  modelName?: string | null
  createTime: string
  updateTime: string
  /** 后端流状态：idle 表示空闲，running 表示仍有 SSE 流 */
  streamStatus?: 'idle' | 'running' | string
}

/** 消息视图对象 */
export interface MessageVO {
  id: number | string
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

/** Agent 最大迭代次数上限（前端输入框限制） */
export const AGENT_MAX_ITERATIONS_LIMIT = 9999

/** Agent 最小迭代次数 */
export const AGENT_MIN_ITERATIONS_LIMIT = 1

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
  sourceType?: DatasetSourceType | string
  sourceConfig?: string
  tableIds: string
  tableNames: string
  status: string
  rowCount: number
  columnCount: number
  ownerId: number
  workspaceId: number
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

/** 统一数据集来源类型（与 DataAgent DatasetSourceType 保持一致） */
export type DatasetSourceType = 'JDBC_TABLE' | 'JDBC_SQL' | 'ALOUDATA_ANALYSIS_VIEW' | 'HTTP_API' | 'FILE'

/** Python/预览可见的数据集字段描述 */
export interface DatasetInputColumn {
  name: string
  title: string
  dataType: string
  nullable: boolean
  semanticRole?: string | null
}

/** Python/预览使用的数据集输入描述；不包含连接凭据 */
export interface DatasetInputDescriptor {
  datasetId: string
  inputName: string
  sourceType: DatasetSourceType | string
  schema: DatasetInputColumn[]
  rowCount?: number | null
  dataRef?: DatasetObjectRef | null
}

/** 大结果集的对象引用；小结果集由 rows 内联返回 */
export interface DatasetObjectRef {
  uri: string
  contentType: string
  sizeBytes: number
  sha256: string
  expiresAt: string
}

/** 统一数据集过滤条件，filters 会由服务端按来源能力下推 */
export interface DatasetFilter {
  field: string
  role: 'dimension' | 'measure'
  operator: 'eq' | 'neq' | 'gt' | 'gte' | 'lt' | 'lte' | 'in' | 'not_in' | 'between' | 'is_null' | 'is_not_null'
  value?: unknown
}

/** 统一数据集读取/预览请求 */
export interface DatasetReadRequest {
  datasetId: string
  inputName: string
  columns?: string[]
  filters?: DatasetFilter[]
  limit?: number
  offset?: number
  parameters?: Record<string, unknown>
}

/** 统一数据集读取结果 */
export interface DatasetBatch {
  rows?: Record<string, unknown>[] | null
  /** 字段结构（列名列表）；后端草稿预览对 JDBC/Aloudata/接口/文件均会返回 */
  schema?: string[] | null
  objectRef?: DatasetObjectRef | null
  rowCount: number
  last: boolean
  pushdownReport?: {
    pushedFilters: DatasetFilter[]
    residualFilters: DatasetFilter[]
    projectionPushed: boolean
    limitPushed: boolean
    sourceQueryDigest?: string | null
  } | null
}

/** 模型类型枚举 */
export const MODEL_TYPES = [
  { value: 'chat', label: '对话' },
  { value: 'embedding', label: '向量' },
  { value: 'rerank', label: '重排' },
] as const

/** 技能实体（代理 mateclaw-server） */
export interface Skill {
  id: number
  /** 技能 slug，唯一标识 */
  name: string
  /** 中文显示名 */
  nameZh: string
  /** 英文显示名 */
  nameEn: string
  /** 技能描述 */
  description: string
  /** 技能类型：builtin（内置）/ custom（自定义）/ mcp（MCP协议） */
  skillType: string
  /** 技能图标（emoji 或 URL） */
  icon: string
  /** 技能版本 */
  version: string
  /** 技能作者 */
  author: string
  /** 是否启用 */
  enabled: boolean
  /** 是否系统内置（不可删除） */
  builtin: boolean
  /** 标签（逗号分隔） */
  tags: string
  /** 拥有者工作区 */
  workspaceId: number
  /** 安全扫描状态 */
  securityScanStatus: string
  /** 生命周期状态：active / stale / archived */
  lifecycleState: string
  /** 是否被钉住 */
  pinned: boolean
  createTime: string
  updateTime: string
}

/** 技能类型选项（对齐后端 SkillEntity.skillType 实际值：builtin / custom / mcp） */
export const SKILL_TYPE_OPTIONS = [
  { value: 'all', label: '全部' },
  { value: 'builtin', label: '内置' },
  { value: 'mcp', label: 'MCP' },
  { value: 'custom', label: '自定义' },
] as const

/** 技能分页响应（对齐 MyBatis Plus IPage 结构） */
export interface SkillPage {
  records: Skill[]
  total: number
  size: number
  current: number
  pages: number
}

/** 技能市场（ClawHub）摘要 */
export interface HubSkillInfo {
  name: string
  slug: string
  description: string
  author: string
  version: string
  icon: string
  tags: string[]
  downloads: number
  bundleUrl: string
}

/** 技能安装请求 */
export interface SkillInstallRequest {
  /** bundle URL（GitHub 仓库 URL 或 ClawHub skill URL） */
  bundleUrl: string
  /** 版本（git ref / hub version，可选） */
  version?: string
  /** 安装后是否启用，默认 true */
  enable?: boolean
  /** 指定 skill 名称（覆盖 SKILL.md 中的名称） */
  targetName?: string
  /** 同名 skill 已存在时是否覆盖，默认 false */
  overwrite?: boolean
  /** 所属工作区 ID */
  workspaceId?: number
}

/** 技能安装任务状态枚举 */
export type InstallStatus = 'PENDING' | 'INSTALLING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'

/** 技能安装结果 */
export interface SkillInstallResult {
  name: string
  enabled: boolean
  sourceUrl: string
  sourceType: string
}

/** 技能安装任务 */
export interface SkillInstallTask {
  taskId: string
  bundleUrl: string
  status: InstallStatus
  error?: string
  result?: SkillInstallResult
  createdAt: string
  updatedAt: string
}

/** 字段级语义模型 */
export interface SemanticModel {
  id: string
  datasourceId: string
  /** 创建者用户 ID（资源归属人） */
  ownerId?: number | string | null
  tableName: string
  columnName: string
  businessName: string
  businessDescription: string
  synonyms: string
  dataType: string
  columnComment: string
  exampleValues: string
  enumValues: string
  unit: string
  valueRange: string
  /** 状态：0-停用 / 1-启用 */
  status: number
  promptInfo: string
  createTime: string
  updateTime: string
}

/** 创建语义模型请求 */
export interface SemanticModelCreateRequest {
  datasourceId: string
  tableName: string
  columnName: string
  businessName?: string
  businessDescription?: string
  synonyms?: string
  dataType?: string
  columnComment?: string
  exampleValues?: string
  enumValues?: string
  unit?: string
  valueRange?: string
}

/** 更新语义模型请求 */
export interface SemanticModelUpdateRequest {
  businessName?: string
  businessDescription?: string
  synonyms?: string
  exampleValues?: string
  enumValues?: string
  unit?: string
  valueRange?: string
  status?: number
}

/** 逻辑外键关系 */
export interface LogicalRelation {
  id: string
  datasourceId: string
  /** 创建者用户 ID（资源归属人） */
  ownerId?: number | string | null
  sourceTableName: string
  sourceColumnName: string
  targetTableName: string
  targetColumnName: string
  /** 关系类型：1:1 / 1:N / N:1 */
  relationType: string
  description: string
  promptInfo: string
  createTime: string
  updateTime: string
}

/** 创建逻辑外键关系请求 */
export interface LogicalRelationCreateRequest {
  datasourceId: string
  sourceTableName: string
  sourceColumnName: string
  targetTableName: string
  targetColumnName: string
  relationType?: string
  description?: string
}

/** 更新逻辑外键关系请求 */
export interface LogicalRelationUpdateRequest {
  relationType?: string
  description?: string
}

/** 关系类型选项 */
export const RELATION_TYPE_OPTIONS = [
  { value: '1:1', label: '1:1' },
  { value: '1:N', label: '1:N' },
  { value: 'N:1', label: 'N:1' },
] as const

/** Schema 语义检索请求 */
export interface SchemaSearchRequest {
  datasourceId: string
  query: string
  topK?: number
  similarityThreshold?: number
}

/** Schema 检索结果 - 表级命中项 */
export interface TableHit {
  tableName: string
  tableComment: string
  score: number
  /** 匹配来源：keyword / semantic / hybrid */
  matchSource: string
  semanticFields: SemanticModel[]
  sampleData: string
}

/** Schema 语义检索结果 */
export interface SchemaSearchResult {
  tableHits: TableHit[]
  relations: LogicalRelation[]
  elapsedMs: number
}

/** 业务术语 */
/** 业务术语关联引用（指标/维度） */
export interface BusinessTermRef {
  /** 指标/维度记录 ID（同步快照，仅辅助展示） */
  id?: string | number | null
  /** 关联数据源 ID */
  datasourceId: string | number
  /** 数据源名称 */
  datasourceName?: string
  /** 指标英文名 / 维度英文名（稳定标识） */
  name: string
  /** 指标展示名 / 维度中文名 */
  displayName?: string
}

export interface BusinessTerm {
  id: string
  tenantCode: string
  termName: string
  synonyms: string
  description: string
  calculationFormula: string
  dataCaliber: string
  dataSource: string
  owner: string
  businessRule: string
  relatedTerms: string
  relatedMetrics: BusinessTermRef[]
  relatedDimensions: BusinessTermRef[]
  example: string
  securityLevel: string
  category: string
  parentId: string | null
  parentTermName: string | null
  /** 状态：0-停用 / 1-启用 */
  status: number
  promptInfo: string
  createTime: string
  updateTime: string
}

/** 创建业务术语请求 */
export interface BusinessTermCreateRequest {
  tenantCode: string
  termName: string
  synonyms?: string
  description?: string
  calculationFormula?: string
  dataCaliber?: string
  dataSource?: string
  owner?: string
  businessRule?: string
  relatedTerms?: string
  relatedMetrics?: BusinessTermRef[]
  relatedDimensions?: BusinessTermRef[]
  example?: string
  securityLevel?: string
  category?: string
  parentId?: string | null
}

/** 更新业务术语请求 */
export interface BusinessTermUpdateRequest {
  termName?: string
  synonyms?: string
  description?: string
  calculationFormula?: string
  dataCaliber?: string
  dataSource?: string
  owner?: string
  businessRule?: string
  relatedTerms?: string
  relatedMetrics?: BusinessTermRef[]
  relatedDimensions?: BusinessTermRef[]
  example?: string
  securityLevel?: string
  category?: string
  parentId?: string | null
  status?: number
}

/** 术语语义检索命中项 */
export interface BusinessTermHit {
  termName: string
  synonyms: string | null
  description: string | null
  calculationFormula: string | null
  dataCaliber: string | null
  businessRule: string | null
  category: string | null
  parentTermName: string | null
  relatedMetricNames: string[] | null
  relatedDimensionNames: string[] | null
  score: number
  matchSource: string
}

/** 术语语义检索结果 */
export interface BusinessTermSearchResult {
  query: string
  tenantCode: string
  termHits: BusinessTermHit[]
  elapsedMs: number
}

/** 业务术语关联引用候选 */
export interface BusinessTermReferenceOptions {
  metrics: BusinessTermRef[]
  dimensions: BusinessTermRef[]
}

/** 帮助文档分类 */
export interface HelpCategory {
  id: string
  name: string
  parentId: string
  sortOrder: number
  icon: string
  description: string
  children: HelpCategory[]
  documentCount: number
  createTime: string
  updateTime: string
}

// ==================== 洞察仪表盘 ====================

/** 洞察仪表盘组件类型 */
export type InsightComponentType = 'kpi' | 'chart' | 'table' | 'filter' | 'timeFilter' | 'aiAnalysis' | 'combination'

/** 筛选器作用范围 */
export type FilterScope = 'global' | 'scoped'

/** 图表子类型 */
export type ChartType =
  | 'line' | 'bar' | 'pie' | 'area' | 'scatter' | 'radar'
  | 'effectScatter' | 'candlestick' | 'heatmap' | 'boxplot'
  | 'map' | 'lines' | 'graph' | 'tree' | 'treemap'
  | 'sunburst' | 'parallel' | 'gauge' | 'funnel'
  | 'sankey' | 'themeRiver' | 'pictorialBar'

/** 栅格位置（grid-layout-plus 坐标系） */
export interface ComponentPosition {
  x: number
  y: number
  w: number
  h: number
}

/** 组件数据绑定配置 */
export interface ComponentDataSource {
  /** 数据源 ID */
  datasourceId: string
  /** 指标名称列表 */
  metrics: string[]
  /** 维度名称列表 */
  dimensions: string[]
  /** 过滤条件（结构化存储，后端构建查询时转换为表达式字符串） */
  filters: Array<Record<string, unknown>>
  /** 指标日期范围约束表达式（可选，静态配置；运行时筛选会覆盖） */
  timeConstraint?: string
  /** 返回行数限制 */
  limit?: number
  /** 新版统一数据集绑定；旧 metrics/dimensions 字段保留用于 Aloudata 兼容。 */
  sourceType?: 'ALOUDATA' | 'JDBC' | 'HTTP_API' | 'FILE' | string
  datasetId?: string
  sql?: string
  analysisViewId?: string
  apiDefinitionId?: string
  objectId?: string
}

/** 组件统一数据集输入绑定（多源 Python 预处理使用）。 */
export interface DataInputBinding {
  inputId: string
  datasetId: string
  alias: string
  filters?: DatasetFilter[]
}

export interface PythonTransformConfig {
  enabled: boolean
  code: string
  parameters?: DashboardScriptParameter[]
}

/** 仪表盘组件定义 */
export interface InsightComponent {
  /** 组件唯一 ID */
  id: string
  /** 组件类型 */
  type: InsightComponentType
  /** 组件标题 */
  title: string
  /** 栅格位置 */
  position: ComponentPosition
  /** 数据绑定配置 */
  dataSource?: ComponentDataSource
  /** 图表子类型（仅 chart 类型组件） */
  chartType?: ChartType
  /** 渲染类型：echarts / kpi / table */
  renderType?: string
  /** 组件扩展配置 */
  config?: Record<string, unknown>
  /** 绑定的筛选器 ID 列表（绑定后该组件仅响应专属筛选器，不再受全局筛选器影响） */
  boundFilterIds?: string[]
  /** 是否启用组件级时间筛选（右上角时间选择器） */
  enableTimeFilter?: boolean
  /** AI 分析内容（Markdown，生成后持久化到 Schema，刷新不丢失） */
  aiAnalysisContent?: string
  /** 多 Tab 配置（可选，配置后组件渲染为多 Tab 切换模式） */
  tabs?: ComponentTab[]
  /** 组件所属视角 ID 列表（空或未配置时表示在所有视角显示） */
  perspectiveIds?: string[]
  /** 是否启用多指标模式（仅 kpi 类型，开启后卡片同时展示多个指标） */
  multiKpi?: boolean
  /** 组合卡片：子卡片列表（默认 Tab / 无页签时生效） */
  children?: InsightCombinationChild[]
  /** 组合卡片：容器配置（标题、背景、圆角、内边距、布局模式、页签等） */
  containerConfig?: InsightCombinationConfig
  /** KPI 指标分组：指标列表（由「最终结果集」字段逐列投影生成，不可手动新增/删除） */
  kpiMetrics?: KpiMetricConfig[]
}

/** KPI 指标 · 单字段样式（展示名 / 指标值 / 单位 / 辅助说明 各自独立） */
export interface KpiMetricFieldStyle {
  /** 字号 px */
  size: number
  /** 字体键（映射见 utils/kpi-metrics.ts 的 KPI_FONT_FAMILY） */
  family: string
  /** 颜色（HEX，如 #1f2329） */
  color: string
  /** 字重 */
  bold: 'bold' | 'normal'
}

/** KPI 指标 · 四个字段的样式集合 */
export interface KpiMetricStyles {
  /** 展示名样式 */
  name: KpiMetricFieldStyle
  /** 指标值样式 */
  value: KpiMetricFieldStyle
  /** 单位样式 */
  unit: KpiMetricFieldStyle
  /** 辅助说明样式 */
  helper: KpiMetricFieldStyle
}

/** KPI 指标（由结果集字段逐列投影；字段映射不可编辑，仅展示配置可编辑） */
export interface KpiMetricConfig {
  /** 结果集字段名（技术主键、只读标识，作为指标稳定 key；**不存展示名**） */
  fieldKey: string
  /**
   * 展示列名。
   * @deprecated 展示名唯一登记在**数据集字段注册表**（`DatasetConfig.fields[].displayName`）一份；
   * 本字段是序列化 / 画布渲染用的**具化镜像**，读写请走注册表（useInsight 的 `setFieldDisplayName`），
   * 持久化时由 `materializedKpiMetrics()` 统一具化。见 docs/策略解读/字段名与展示名契约-实施计划.md。
   */
  displayName: string
  /**
   * 单位（字段级展示配置，留空不显示）。
   * @deprecated 同 displayName：唯一登记在字段注册表（`DatasetConfig.fields[].unit`），本字段为其镜像。
   */
  unit: string
  /** 辅助说明（固定文本，用户可编辑） */
  helperText: string
  /** 是否在卡片中展示 */
  visible: boolean
  /** 自由布局：距卡片内容区左侧 px */
  x: number
  /** 自由布局：距卡片内容区顶部 px */
  y: number
  /** 自由布局：宽 px */
  w: number
  /** 自由布局：高 px */
  h: number
  /** 各字段样式 */
  styles: KpiMetricStyles
}

/** 组合卡片子卡片自由布局坐标（相对容器内容区左上角，单位 px） */
export interface CombinationChildLayout {
  /** 距内容区左边 px */
  x: number
  /** 距内容区顶部 px */
  y: number
  /** 宽度列数（1~12，渲染为百分比宽） */
  col: number
  /** 高度 px（可选，缺省由内容自适应） */
  h?: number
}

/** 组合卡片子卡片（本质是精简版仪表盘组件，定位用 layout 而非栅格 position） */
export interface InsightCombinationChild {
  /** 子卡片唯一 ID */
  id: string
  /** 子卡片类型（复用顶层组件类型） */
  type: InsightComponentType
  /** 子卡片标题 */
  title: string
  /** 图表子类型（仅 chart） */
  chartType?: ChartType
  /** 组件扩展配置 */
  config?: Record<string, unknown>
  /** 数据绑定配置（v1 暂未接入取数，结构预留） */
  dataSource?: ComponentDataSource
  /** 自由布局坐标 */
  layout: CombinationChildLayout
  /**
   * 以下三个字段与 `InsightComponent` 同名语义一致：容器内的子卡片同样是可配置组件，
   * 需要承载「绑定的筛选器 / 组件级时间筛选 / 多指标模式」。
   * 此前类型未声明而代码已在赋值（`handleComponentChange` 写回 child 时），
   * 导致 vue-tsc 报 TS2339，实际运行正常。
   */
  /** 绑定的筛选器 ID 列表（绑定后仅响应专属筛选器） */
  boundFilterIds?: string[]
  /** 是否启用组件级时间筛选（右上角时间选择器） */
  enableTimeFilter?: boolean
  /** 是否启用多指标模式（仅 kpi 子卡片） */
  multiKpi?: boolean
}

/** 组合卡片页签（每个页签拥有独立的子卡片集合） */
export interface CombinationTab {
  /** 页签唯一 ID */
  id: string
  /** 页签标题 */
  title: string
  /** 该页签下的子卡片 */
  children: InsightCombinationChild[]
}

/** 组合卡片容器配置 */
export interface InsightCombinationConfig {
  /** 容器标题 */
  title: string
  /** 是否显示标题 */
  showTitle: boolean
  /** 背景色（CSS color） */
  background: string
  /** 圆角 px */
  radius: number
  /** 内边距 px */
  padding: number
  /** 内部布局模式：自由布局 / 栅格 / 垂直流 */
  layoutMode: 'free' | 'grid' | 'vertical'
  /** 页签列表（非空时启用多页签） */
  tabs: CombinationTab[]
  /** 当前激活页签 ID（tabs 非空时生效） */
  activeTab?: string
  /** 容器边框样式 */
  style: {
    border: {
      enabled: boolean
      color: string
    }
  }
}

/**
 * 结果集：一次成功产出的、可直接驱动卡片渲染的数据集合。
 * <p>
 * 设计契约见 docs/策略解读/卡片数据链路与结果集交互方案-讨论稿.md：
 * - 卡片只能从结果集取数；数据集 / 筛选条件 / Python 脚本都只是「怎么产出结果集」的手段；
 * - 无脚本时结果集 = 单个数据集的查询结果（source=dataset）；
 * - 有脚本时结果集 = 脚本输出（source=script，行数据由 executionId 向后端回读）；
 * - 输入配置变更后旧结果集标记为过期（stale），卡片沿用旧渲染并提示刷新。
 */
export interface ComponentResultSet {
  /** 结果集来源：数据集直通 / 脚本输出 */
  source: 'dataset' | 'script'
  /** 结果集状态（持久化只保留 ready / failed，stale 与 running 属运行期状态） */
  status: 'ready' | 'failed'
  /** 结果集字段结构（「指标配置」的候选字段唯一来源） */
  columns: { name: string; type: string }[]
  /** 结果集行数 */
  rowCount: number
  /** 生成时间（ISO 字符串） */
  generatedAt: string
  /** 生成耗时（毫秒） */
  elapsedMs?: number
  /** 脚本结果的执行 ID（source=script 时用于回读行数据） */
  executionId?: string
  /** 失败原因（status=failed 时） */
  error?: string
}

/** 当前组件的数据集编排配置；输入归属于组件，不再使用仪表盘根级脚本输入。 */
export interface ComponentDatasetPipeline {
  datasetInputs: DashboardDatasetInput[]
  scriptFilterBindings?: DashboardScriptFilterBinding[]
  script?: string
  parameters?: DashboardScriptParameter[]
  executionPolicy?: DashboardExecutionPolicy
  /** 最近一次产出的结果集（元数据持久化，行数据由后端 executionId 或前端重算获得） */
  resultSet?: ComponentResultSet
}

/** 组件 Tab 配置（每个 Tab 拥有独立的数据源配置） */
export interface ComponentTab {
  /** Tab 唯一 ID */
  id: string
  /** Tab 标题 */
  title: string
  /** Tab 数据源配置 */
  dataSource: ComponentDataSource
}

/** 仪表盘视角（顶层 Tab） */
export interface DashboardPerspective {
  /** 视角唯一 ID */
  id: string
  /** 视角显示名称 */
  name: string
  /** 视角图标（可选，emoji 或 icon class） */
  icon?: string
}

/** 仪表盘页面（多级菜单） */
export interface DashboardPage {
  /** 页面唯一 ID */
  id: string
  /** 页面显示名称（菜单标题） */
  name: string
  /** 页面图标（可选，emoji 或 icon class） */
  icon?: string
  /** 父页面 ID（可选，设置后形成多级菜单树） */
  parentId?: string
  /** 页面排序（可选，越小越靠前） */
  order?: number
  /** 该页面下的组件列表 */
  components: InsightComponent[]
}

/** 仪表盘脚本输入绑定；脚本只通过 inputName 读取，不直接使用连接信息。 */
export interface DashboardDatasetInput {
  datasetId: string
  inputName: string
  displayName?: string
  /** 数据集来源模式；缺省按旧数据集表读取。 */
  sourceType?: DatasetSourceType | string
  /** 来源配置不包含连接凭据，仅保存查询/视图引用。 */
  sourceConfig?: {
    datasourceId?: string
    sql?: string
    analysisViewId?: string
    apiDefinitionId?: string
    objectId?: string
    /** Aloudata「指标 & 维度」模式的指标列表（重开仪表盘时据此重建结果集） */
    metrics?: string[]
    /** Aloudata「指标 & 维度」模式的维度列表 */
    dimensions?: string[]
  }
  /**
   * 字段映射（契约定版，见 docs/策略解读/字段名与展示名契约-实施计划.md §4.3）：
   *   - `source` = **字段名**（技术主键，组件生命周期内不可变），后端下推 SQL / 脚本取值**唯一依据**；
   *   - `target` = **展示名**（表现层标签，数据集内唯一、可空回退 source），**仅**用于导出表头等展示场景，
   *     后端**不得**用它下推。
   */
  fieldMappings?: Array<{ source: string; target: string }>
  /** 当前输入数据集的源端筛选；`field` 自本版本起恒为**字段名**（老配置的展示名在读入时惰性归一）。 */
  filters?: DatasetFilter[]
}

/** 仪表盘脚本参数定义；参数只描述作用域，不绑定具体字段。 */
export interface DashboardScriptParameter {
  name: string
  type: 'string' | 'number' | 'boolean' | 'date' | 'datetime' | 'enum' | 'date_range' | 'string[]' | 'number[]'
  options?: string[]
  defaultValue?: unknown
  required?: boolean
  scope: 'dashboard' | 'page' | 'component'
}

/** 脚本输入筛选器到数据集的声明式绑定。 */
export interface DashboardScriptFilterBinding {
  filterComponentId: string
  inputNames: string[]
  /** 别名 → 绑定的字段名（技术主键；不存展示名，改名不影响绑定关系）。 */
  fieldMappings?: Record<string, string>
}

/** Python Runner 执行限制（不包含依赖安装配置）。 */
export interface DashboardExecutionPolicy {
  timeoutSeconds?: number
  maxRows?: number
  maxOutputBytes?: number
}

/** 脚本结果到现有仪表盘组件的显式绑定。 */
export interface DashboardScriptBinding {
  componentId: string
  renderType: 'table' | 'echarts'
}

/** 仪表盘 Schema */
export interface InsightDashboardSchema {
  version: string
  /** 页面列表（多级菜单，每个页面拥有独立组件列表） */
  pages: DashboardPage[]
  /** 可选的数据集脚本输入；旧 Schema 缺失时按空列表处理 */
  datasetInputs?: DashboardDatasetInput[]
  /** Python 处理脚本草稿；平台不自动生成或执行 AI 脚本 */
  script?: string
  /** 脚本参数定义，不包含字段绑定 */
  parameters?: DashboardScriptParameter[]
  scriptFilterBindings?: DashboardScriptFilterBinding[]
  /** Runner 资源与超时策略 */
  executionPolicy?: DashboardExecutionPolicy
  /** 用户确认后的脚本结果组件绑定 */
  scriptBindings?: DashboardScriptBinding[]
}

/** 洞察仪表盘实体 */
export interface InsightDashboard {
  id: string
  name: string
  description: string
  /** Schema JSON 字符串（components 数组序列化） */
  schemaJson: string
  status: 'draft' | 'published'
  /** AI 解读使用的 Agent ID */
  agentId?: string
  workspaceId: string
  ownerId?: string
  ownerName?: string
  modifier?: string
  createTime: string
  updateTime: string
}

/** 创建仪表盘输入 */
export interface InsightDashboardCreateInput {
  name: string
  description?: string
  schemaJson?: string
  agentId?: string
  ownerName?: string
}

/** 更新仪表盘输入 */
export interface InsightDashboardUpdateInput {
  name?: string
  description?: string
  schemaJson?: string
  status?: string
  agentId?: string
  ownerName?: string
}

/** AI助手对话输入（统一AI生成和AI修改） */
export interface InsightDashboardAiChatInput {
  /** 仪表盘ID，修改模式必填，生成模式为空 */
  dashboardId?: string
  /** 仪表盘名称，仅生成模式使用 */
  name?: string
  /** 数据源ID，仅生成模式使用 */
  datasourceId?: string
  /** 用户消息/指令 */
  message: string
  /** 历史对话消息（用于多轮对话上下文，刷新页面后丢失） */
  historyMessages?: ChatHistoryMessage[]
}

/** 历史对话消息（用于多轮对话上下文传递） */
export interface ChatHistoryMessage {
  /** 角色：user 或 assistant */
  role: 'user' | 'assistant'
  /** 消息内容 */
  content: string
}

/** KPI 单条指标渲染数据（后端取数结果） */
export interface KpiItemData {
  /** 指标名（后端返回；前端优先用 kpiMetrics.displayName 覆盖） */
  name: string
  /** 指标值（已格式化字符串） */
  value: string
  /** 环比/趋势百分比 */
  chg?: string
  /** 趋势方向 */
  up?: boolean
  /** 来源结果集字段名（KPI 指标分组按此与 kpiMetrics 对齐） */
  fieldKey?: string
  /** 单位（后端可选下发；缺省时用 kpiMetrics.unit） */
  unit?: string
  /** 辅助说明（后端可选下发；缺省时用 kpiMetrics.helperText） */
  helperText?: string
}

/** 组件渲染数据（后端取数 + 图表构建后返回） */
export interface InsightComponentData {
  /** 对应组件 ID */
  componentId: string
  /** 渲染类型：echarts / kpi / table / aiAnalysis */
  renderType: 'echarts' | 'kpi' | 'table' | 'aiAnalysis'
  /** ECharts option（renderType=echarts 时） */
  option?: Record<string, unknown>
  /** KPI 卡片数据（renderType=kpi 时，单指标模式） */
  kpi?: KpiItemData
  /** KPI 多指标数据列表（renderType=kpi 且 multiKpi=true 时，按指标逐列展示） */
  kpiList?: KpiItemData[]
  /** 表格数据（renderType=table 时） */
  table?: {
    columns: string[]
    rows: string[][]
  }
  /** AI 分析数据（renderType=aiAnalysis 时） */
  aiAnalysis?: {
    /** 模板填充后的数据部分（Markdown） */
    dataSection: string
    /** AI 生成的分析内容（Markdown，可能为空表示尚未生成） */
    analysisSection?: string
  }
  /** 取数失败时的错误信息（前端展示降级提示） */
  error?: string
  /** 多 Tab 渲染数据（key = tabId，仅当组件配置了 tabs 时有值） */
  tabs?: Record<string, ComponentTabData>
}

/** 单个 Tab 的渲染数据 */
export interface ComponentTabData {
  /** Tab 标题 */
  title: string
  /** ECharts option（renderType=echarts 时） */
  option?: Record<string, unknown>
  /** KPI 卡片数据（renderType=kpi 时，单指标模式） */
  kpi?: KpiItemData
  /** KPI 多指标数据列表（renderType=kpi 且 multiKpi=true 时） */
  kpiList?: KpiItemData[]
  /** 表格数据（renderType=table 时） */
  table?: { columns: string[]; rows: string[][] }
  /** 取数失败时的错误信息 */
  error?: string
}

/** 时间范围预设类型 */
export type TimeRangePreset = 'today' | '7d' | '30d' | '90d' | 'custom'

/** 时间范围筛选值 */
export interface TimeRangeValue {
  /** 预设类型 */
  preset: TimeRangePreset
  /** 自定义起始日期（preset=custom 时使用，格式 yyyy-MM-dd） */
  start?: string
  /** 自定义结束日期（preset=custom 时使用，格式 yyyy-MM-dd） */
  end?: string
}

/** 维度筛选值 */
export interface FilterValue {
  /** 筛选字段名（对应维度名） */
  field: string
  /** 筛选值 */
  value: string | string[]
}

/** 仪表盘运行时筛选上下文 */
export interface DashboardFilterContext {
  /** 时间范围筛选 */
  timeRange?: TimeRangeValue
  /** 维度筛选值列表 */
  dimensionFilters: FilterValue[]
  /** 触发此次筛选的筛选器组件 ID（用于区分全局/组件绑定筛选） */
  sourceFilterId?: string
}

/** 筛选组件配置（InsightComponent.config 的约定结构） */
export interface FilterComponentConfig {
  /** 筛选字段（维度名） */
  field: string
  /** 选项来源：static（静态） / dynamic（动态从数据源获取） */
  optionSource: 'static' | 'dynamic'
  /** 静态选项（optionSource=static 时使用） */
  staticOptions?: Array<{ label: string; value: string }>
  /** 数据源 ID（用于加载维度列表和动态获取维度值） */
  datasourceId?: string
  /** 动态选项维度名（optionSource=dynamic 时使用，已废弃，直接使用 field） */
  dimension?: string
  /** 作用范围：global（全局，影响所有未绑定专属筛选器的组件）/ scoped（仅影响绑定的组件） */
  scope?: FilterScope
  /** 影响的目标组件 ID 列表（scope=scoped 时使用，空表示全局） */
  targetComponentIds?: string[]
}

/** 时间筛选组件配置（InsightComponent.config 的约定结构） */
export interface TimeFilterComponentConfig {
  /** 时间维度字段名（默认 metric_time） */
  field: string
  /** 允许的预设选项列表 */
  availablePresets?: TimeRangePreset[]
  /** 作用范围：global（全局，影响所有未绑定专属筛选器的组件）/ scoped（仅影响绑定的组件） */
  scope?: FilterScope
  /** 影响的目标组件 ID 列表（scope=scoped 时使用，空表示全局） */
  targetComponentIds?: string[]
}

/** AI 分析组件配置（InsightComponent.config 的约定结构） */
export interface AIAnalysisComponentConfig {
  /** 分析提示词模板（可选，用于自定义分析方向） */
  promptTemplate?: string
  /** 是否自动生成（预览时自动触发 AI 分析） */
  autoGenerate?: boolean
}

/** 洞察报告 */
export interface InsightReport {
  id: string
  dashboardId: string
  name: string
  description: string
  /** 报告HTML内容 */
  reportContent: string
  /** ECharts option 数据（JSON 格式，供报告页渲染图表） */
  echartsOptions?: string
  status: 'draft' | 'published'
  /** 当前用户是否已订阅该报告 */
  subscribed?: boolean
  ownerId?: string
  ownerName?: string
  modifier?: string
  createTime: string
  updateTime: string
}

/** 发布报告请求 */
export interface InsightReportPublishInput {
  dashboardId: string
  name?: string
  description?: string
}

/** 时间范围预设选项 */
export const TIME_RANGE_PRESETS: Array<{ value: TimeRangePreset; label: string }> = [
  { value: 'today', label: 'insight.timeRange.today' },
  { value: '7d', label: 'insight.timeRange.7d' },
  { value: '30d', label: 'insight.timeRange.30d' },
  { value: '90d', label: 'insight.timeRange.90d' },
  { value: 'custom', label: 'insight.timeRange.custom' },
]

/** 帮助文档分类请求 */
export interface HelpCategoryRequest {
  name?: string
  parentId?: string
  sortOrder?: number
  icon?: string
  description?: string
}

/** 帮助文档 */
export interface HelpDocument {
  id: string
  categoryId: string
  categoryName: string
  title: string
  content: string
  sortOrder: number
  status: string
  author: string
  /** 标签（逗号分隔） */
  tags: string
  /** 文档摘要 */
  summary: string
  viewCount: number
  createTime: string
  updateTime: string
}

/** 帮助文档请求 */
export interface HelpDocumentRequest {
  categoryId?: string
  title?: string
  content?: string
  sortOrder?: number
  status?: string
  author?: string
  tags?: string
  summary?: string
}

/** 帮助文档搜索结果 */
export interface HelpSearchResult {
  id: string
  categoryId: string
  categoryName: string
  title: string
  /** 匹配的内容摘要（含高亮标记） */
  highlightContent: string
  status: string
  author: string
  viewCount: number
  updateTime: string
}

/** 帮助文档反馈请求 */
export interface HelpFeedbackRequest {
  rating?: number
  suggestion?: string
  userId?: string
}

/** 帮助文档反馈 */
export interface HelpFeedback {
  id: string
  documentId: string
  rating: number
  suggestion: string
  userId: string
  createTime: string
  updateTime: string
}

/** 帮助文档反馈汇总 */
export interface HelpFeedbackSummary {
  documentId: string
  averageRating: number
  totalFeedbacks: number
  star5Count: number
  star4Count: number
  star3Count: number
  star2Count: number
  star1Count: number
}
