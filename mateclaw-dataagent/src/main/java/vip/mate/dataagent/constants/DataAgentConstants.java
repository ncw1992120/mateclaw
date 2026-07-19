package vip.mate.dataagent.constants;

/**
 * DataAgent 常量定义
 */
public final class DataAgentConstants {

    private DataAgentConstants() {
    }

    /** 默认工作区 ID */
    public static final Long DEFAULT_WORKSPACE_ID = 1L;

    /** Schema 发现状态：待发现 */
    public static final String SCHEMA_STATUS_PENDING = "pending";

    /** Schema 发现状态：发现中 */
    public static final String SCHEMA_STATUS_RUNNING = "running";

    /** Schema 发现状态：已完成 */
    public static final String SCHEMA_STATUS_COMPLETED = "completed";

    /** Schema 发现状态：发现失败 */
    public static final String SCHEMA_STATUS_FAILED = "failed";

    /** 数据源类型：MySQL */
    public static final String SOURCE_TYPE_MYSQL = "mysql";

    /** 数据源类型：PostgreSQL */
    public static final String SOURCE_TYPE_POSTGRESQL = "postgresql";

    /** 数据源类型：Oracle */
    public static final String SOURCE_TYPE_ORACLE = "oracle";

    /** 数据源类型：Snowflake */
    public static final String SOURCE_TYPE_SNOWFLAKE = "snowflake";

    /** 数据源类型：BigQuery */
    public static final String SOURCE_TYPE_BIGQUERY = "bigquery";

    /** 数据源类型：Redshift */
    public static final String SOURCE_TYPE_REDSHIFT = "redshift";

    /** 数据源类型：ClickHouse */
    public static final String SOURCE_TYPE_CLICKHOUSE = "clickhouse";

    /** 数据源类型：Doris */
    public static final String SOURCE_TYPE_DORIS = "doris";

    /** 数据源类型：MongoDB */
    public static final String SOURCE_TYPE_MONGODB = "mongodb";

    /** 数据源类型：Elasticsearch */
    public static final String SOURCE_TYPE_ELASTICSEARCH = "elasticsearch";

    /** 数据源类型：CSV */
    public static final String SOURCE_TYPE_CSV = "csv";

    /** 数据源类型：Excel */
    public static final String SOURCE_TYPE_EXCEL = "excel";

    /** 数据源类型：Parquet */
    public static final String SOURCE_TYPE_PARQUET = "parquet";

    /** 数据源类型：API */
    public static final String SOURCE_TYPE_API = "api";

    /** 数据源类型：Kafka */
    public static final String SOURCE_TYPE_KAFKA = "kafka";

    /** 数据源类型：Aloudata */
    public static final String SOURCE_TYPE_ALOUDATA = "aloudata";

    /** 表类型：普通表 */
    public static final String TABLE_TYPE_TABLE = "table";

    /** 表类型：视图 */
    public static final String TABLE_TYPE_VIEW = "view";

    /** 表类型：物化视图 */
    public static final String TABLE_TYPE_MATERIALIZED_VIEW = "materialized_view";

    /** 表类型：外部表 */
    public static final String TABLE_TYPE_EXTERNAL = "external";

    /** 同步模式：追加（仅新增字段） */
    public static final String SYNC_MODE_APPEND = "append";

    /** 同步模式：覆盖（删除旧字段重新发现） */
    public static final String SYNC_MODE_OVERWRITE = "overwrite";

    /** 数据预览默认行数限制 */
    public static final int PREVIEW_DEFAULT_LIMIT = 100;

    /** 数据预览最大行数限制 */
    public static final int PREVIEW_MAX_LIMIT = 500;

    /** 数据集状态：草稿 */
    public static final String DATASET_STATUS_DRAFT = "draft";

    /** 数据集状态：就绪 */
    public static final String DATASET_STATUS_READY = "ready";

    /** 数据集状态：错误 */
    public static final String DATASET_STATUS_ERROR = "error";

    /** 字段分类：维度 */
    public static final String FIELD_CATEGORY_DIMENSION = "dimension";

    /** 字段分类：度量 */
    public static final String FIELD_CATEGORY_MEASURE = "measure";

    /** 数据集默认每页条数 */
    public static final int DATASET_DEFAULT_PAGE_SIZE = 50;

    /** 语义模型状态：停用 */
    public static final int SEMANTIC_STATUS_DISABLED = 0;

    /** 语义模型状态：启用 */
    public static final int SEMANTIC_STATUS_ENABLED = 1;

    /** 逻辑外键关系类型：一对一 */
    public static final String RELATION_TYPE_ONE_TO_ONE = "1:1";

    /** 逻辑外键关系类型：一对多 */
    public static final String RELATION_TYPE_ONE_TO_MANY = "1:N";

    /** 逻辑外键关系类型：多对一 */
    public static final String RELATION_TYPE_MANY_TO_ONE = "N:1";

    /** Schema 嵌入文本当前版本 */
    public static final int SCHEMA_EMBEDDING_TEXT_VERSION = 1;

    /** Schema 语义检索默认 Top-K */
    public static final int SCHEMA_SEARCH_DEFAULT_TOP_K = 10;

    /** Schema 语义检索默认相似度阈值 */
    public static final double SCHEMA_SEARCH_DEFAULT_THRESHOLD = 0.3;

    /** Schema 语义检索 RRF 融合参数 k（值越小，排名靠前的结果优势越明显，提升排序区分度） */
    public static final int SCHEMA_SEARCH_RRF_K = 20;

    /** Schema 关键词检索匹配字段 */
    public static final String[] SCHEMA_KEYWORD_SEARCH_FIELDS = {
            "table_name", "column_name", "business_name", "business_description", "synonyms", "column_comment"
    };

    /** Python 执行器默认代码超时时间（秒） */
    public static final long PYTHON_DEFAULT_CODE_TIMEOUT_SECONDS = 60;

    /** Python 执行器默认最大重试次数 */
    public static final int PYTHON_DEFAULT_MAX_RETRIES = 3;

    /** Python 执行器默认标准输出最大长度 */
    public static final int PYTHON_DEFAULT_MAX_STDOUT_LENGTH = 50000;

    /** Python 执行器默认标准错误最大长度 */
    public static final int PYTHON_DEFAULT_MAX_STDERR_LENGTH = 10000;

    /** 帮助文档状态：草稿 */
    public static final String HELP_DOC_STATUS_DRAFT = "draft";

    /** 帮助文档状态：已发布 */
    public static final String HELP_DOC_STATUS_PUBLISHED = "published";

    /** 帮助分类顶级父 ID */
    public static final Long HELP_CATEGORY_ROOT_PARENT_ID = 0L;

    /** 帮助文档搜索默认返回条数 */
    public static final int HELP_SEARCH_DEFAULT_LIMIT = 20;

    /** 帮助文档搜索最小关键字长度 */
    public static final int HELP_SEARCH_MIN_KEYWORD_LENGTH = 2;

    /** 帮助文档反馈最低评分 */
    public static final int HELP_FEEDBACK_MIN_RATING = 1;

    /** 帮助文档反馈最高评分 */
    public static final int HELP_FEEDBACK_MAX_RATING = 5;

    /** 帮助文档相关推荐默认数量 */
    public static final int HELP_RELATED_DOCS_DEFAULT_LIMIT = 5;

    /** Schema 嵌入 Elasticsearch 索引名称 */
    public static final String SCHEMA_ELASTICSEARCH_INDEX = "dataagent_schema_embedding";

    /** Schema 嵌入 ES 向量检索字段名 */
    public static final String SCHEMA_ES_EMBEDDING_FIELD = "embedding";

    /** Schema 嵌入 ES 嵌入文本字段名 */
    public static final String SCHEMA_ES_EMBEDDING_TEXT_FIELD = "embeddingText";

    /** Elasticsearch 默认向量检索候选数（numCandidates），越大召回率越高但延迟越高 */
    public static final int ES_KNN_NUM_CANDIDATES = 200;

    // ==================== Aloudata 语义层 ====================

    /** Aloudata 指标 ES 索引名称 */
    public static final String ALOUDATA_METRIC_ES_INDEX = "dataagent_aloudata_metric";

    /** Aloudata 维度 ES 索引名称 */
    public static final String ALOUDATA_DIMENSION_ES_INDEX = "dataagent_aloudata_dimension";

    /** Aloudata ES 向量检索字段名 */
    public static final String ALOUDATA_ES_EMBEDDING_FIELD = "embedding";

    /** Aloudata ES 嵌入文本字段名 */
    public static final String ALOUDATA_ES_EMBEDDING_TEXT_FIELD = "embeddingText";

    /** Aloudata 同步：metric_available_dimensions 批量大小 */
    public static final int ALOUDATA_SYNC_BATCH_SIZE = 10;

    /** Aloudata 同步：指标列表分页大小 */
    public static final int ALOUDATA_SYNC_METRIC_PAGE_SIZE = 100;

    /** Aloudata 同步：维度列表分页大小 */
    public static final int ALOUDATA_SYNC_DIMENSION_PAGE_SIZE = 100;

    /** Aloudata 同步：批量 Upsert 大小 */
    public static final int ALOUDATA_SYNC_BATCH_UPSERT_SIZE = 500;

    /** Aloudata 语义检索默认 Top-K */
    public static final int ALOUDATA_SEARCH_DEFAULT_TOP_K = 10;

    /** Aloudata 语义检索默认相似度阈值 */
    public static final double ALOUDATA_SEARCH_DEFAULT_THRESHOLD = 0.3;

    /** Aloudata 指标管理默认每页条数 */
    public static final int ALOUDATA_METRIC_DEFAULT_PAGE_SIZE = 20;

    /** Aloudata 维度管理默认每页条数 */
    public static final int ALOUDATA_DIMENSION_DEFAULT_PAGE_SIZE = 20;

    /** Aloudata 类目类型：指标类目 */
    public static final String ALOUDATA_CATEGORY_TYPE_METRIC = "CATEGORY_METRIC";

    /** Aloudata 类目类型：维度类目 */
    public static final String ALOUDATA_CATEGORY_TYPE_DIMENSION = "CATEGORY_DIMENSION";

    /** 默认 Embedding 向量维度 */
    public static final int DEFAULT_EMBEDDING_DIMENSION = 1024;

    // ==================== 业务术语 ====================

    /** 业务术语状态：停用 */
    public static final int BUSINESS_TERM_STATUS_DISABLED = 0;

    /** 业务术语状态：启用 */
    public static final int BUSINESS_TERM_STATUS_ENABLED = 1;

    /** 业务术语 ES 索引名称 */
    public static final String BUSINESS_TERM_ES_INDEX = "dataagent_business_term";

    /** 业务术语关键词检索匹配字段 */
    public static final String[] BUSINESS_TERM_KEYWORD_SEARCH_FIELDS = {
            "termName", "synonyms", "description", "calculationFormula", "dataCaliber", "businessRule", "category", "embeddingText"
    };

    /** 业务术语语义检索默认 Top-K */
    public static final int BUSINESS_TERM_SEARCH_DEFAULT_TOP_K = 10;

    /** 业务术语语义检索默认相似度阈值 */
    public static final double BUSINESS_TERM_SEARCH_DEFAULT_THRESHOLD = 0.3;

    // ==================== 认证与权限 ====================

    /** 工作区 ID 请求头名称 */
    public static final String HEADER_WORKSPACE_ID = "X-Workspace-Id";

    /** 登录接口路径（SecurityConfig 放行） */
    public static final String AUTH_LOGIN_PATH = "/v1/auth/login";

    /** 全局管理员角色标识 */
    public static final String ROLE_ADMIN = "admin";

    /** 普通用户角色标识 */
    public static final String ROLE_USER = "user";

    // ==================== 工作区角色 ====================

    /** 工作区角色：拥有者 */
    public static final String WORKSPACE_ROLE_OWNER = "owner";

    /** 工作区角色：管理员 */
    public static final String WORKSPACE_ROLE_ADMIN = "admin";

    /** 工作区角色：成员 */
    public static final String WORKSPACE_ROLE_MEMBER = "member";

    /** 工作区角色：访客 */
    public static final String WORKSPACE_ROLE_VIEWER = "viewer";

    // ==================== 细粒度权限点 ====================
    // 命名约定：资源:动作
    // 权限点与角色的映射关系见 DataAgentPermission 枚举

    // 模型配置（全局 admin only，已由 @RequireGlobalAdmin 控制，此处供前端权限判断复用）
    public static final String PERM_MODEL_VIEW = "model:view";
    public static final String PERM_MODEL_MANAGE = "model:manage";

    // 技能配置
    public static final String PERM_SKILL_VIEW = "skill:view";
    public static final String PERM_SKILL_MANAGE = "skill:manage";

    // 数据配置
    public static final String PERM_DATASOURCE_VIEW = "datasource:view";
    public static final String PERM_DATASOURCE_CREATE = "datasource:create";
    public static final String PERM_DATASOURCE_MANAGE = "datasource:manage";
    public static final String PERM_DATASOURCE_SYNC = "datasource:sync";

    // 业务词典
    public static final String PERM_BUSINESS_TERM_VIEW = "business-term:view";
    public static final String PERM_BUSINESS_TERM_MANAGE = "business-term:manage";

    // 智能体配置
    public static final String PERM_AGENT_VIEW = "agent:view";
    public static final String PERM_AGENT_MANAGE = "agent:manage";

    // 业务知识库
    public static final String PERM_KNOWLEDGE_VIEW = "knowledge:view";
    public static final String PERM_KNOWLEDGE_MANAGE = "knowledge:manage";

    // 工作空间
    public static final String PERM_WORKSPACE_VIEW = "workspace:view";
    public static final String PERM_WORKSPACE_MANAGE = "workspace:manage";
    public static final String PERM_WORKSPACE_MEMBER_VIEW = "workspace:member:view";
    public static final String PERM_WORKSPACE_MEMBER_MANAGE = "workspace:member:manage";

    // ==================== 资源授权 ====================

    /** 资源类型：技能 */
    public static final String RESOURCE_TYPE_SKILL = "skill";

    /** 资源类型：Agent */
    public static final String RESOURCE_TYPE_AGENT = "agent";

    /** 资源类型：数据源 */
    public static final String RESOURCE_TYPE_DATASOURCE = "datasource";

    /** 资源类型：业务词典 */
    public static final String RESOURCE_TYPE_BUSINESS_TERM = "business_term";

    /** 资源类型：知识库 */
    public static final String RESOURCE_TYPE_KNOWLEDGE = "knowledge";

    /** 授权类型：按角色 */
    public static final String GRANT_TYPE_ROLE = "role";

    /** 授权类型：按用户 */
    public static final String GRANT_TYPE_USER = "user";

    /** 授权类型：按用户组 */
    public static final String GRANT_TYPE_GROUP = "group";

    /** 权限：查看（可看到资源的配置信息和元数据） */
    public static final String PERMISSION_VIEW = "view";

    /** 权限：使用（可用该资源发起会话、查询数据等操作） */
    public static final String PERMISSION_USE = "use";

    /** 权限：编辑（可修改资源配置、同步元数据等） */
    public static final String PERMISSION_EDIT = "edit";

    /** 授权状态：已撤销 */
    public static final int GRANT_STATUS_REVOKED = 0;

    /** 授权状态：生效中 */
    public static final int GRANT_STATUS_ACTIVE = 1;

    // ==================== 审批流程 ====================

    /** 审批类型：技能发布 */
    public static final String APPROVAL_TYPE_SKILL_PUBLISH = "skill_publish";

    /** 审批类型：Agent 发布 */
    public static final String APPROVAL_TYPE_AGENT_PUBLISH = "agent_publish";

    /** 审批类型：资源授权 */
    public static final String APPROVAL_TYPE_RESOURCE_GRANT = "resource_grant";

    /** 审批状态：待审批 */
    public static final String APPROVAL_STATUS_PENDING = "pending";

    /** 审批状态：已通过 */
    public static final String APPROVAL_STATUS_APPROVED = "approved";

    /** 审批状态：已拒绝 */
    public static final String APPROVAL_STATUS_REJECTED = "rejected";

    /** 审批状态：已撤回 */
    public static final String APPROVAL_STATUS_CANCELLED = "cancelled";

    // ==================== 洞察仪表盘 ====================

    /** 仪表盘状态：草稿 */
    public static final String INSIGHT_DASHBOARD_STATUS_DRAFT = "draft";

    /** 仪表盘状态：已发布 */
    public static final String INSIGHT_DASHBOARD_STATUS_PUBLISHED = "published";

    /** 组件渲染类型：ECharts 图表 */
    public static final String INSIGHT_RENDER_TYPE_ECHARTS = "echarts";

    /** 组件渲染类型：KPI 卡片 */
    public static final String INSIGHT_RENDER_TYPE_KPI = "kpi";

    /** 组件渲染类型：表格 */
    public static final String INSIGHT_RENDER_TYPE_TABLE = "table";

    /** 图表类型：折线图 */
    public static final String CHART_TYPE_LINE = "line";

    /** 图表类型：柱状图 */
    public static final String CHART_TYPE_BAR = "bar";

    /** 图表类型：饼图 */
    public static final String CHART_TYPE_PIE = "pie";

    /** 图表类型：面积图 */
    public static final String CHART_TYPE_AREA = "area";

    /** 图表类型：散点图 */
    public static final String CHART_TYPE_SCATTER = "scatter";

    /** 图表类型：雷达图 */
    public static final String CHART_TYPE_RADAR = "radar";

    /** 报告模板路径 */
    public static final String INSIGHT_REPORT_TEMPLATE_PATH = "skills/aloudata_metric_query/templates/report-template.md";

    /** AI 解读会话 ID 前缀 */
    public static final String INSIGHT_REPORT_CONVERSATION_PREFIX = "insight-report-";

    /** 仪表盘筛选：过滤操作符 - 包含 */
    public static final String INSIGHT_FILTER_OP_IN = "in";

    /** 仪表盘筛选：时间预设 - 今天 */
    public static final String INSIGHT_TIME_PRESET_TODAY = "today";

    /** 仪表盘筛选：时间预设 - 近7天 */
    public static final String INSIGHT_TIME_PRESET_7D = "7d";

    /** 仪表盘筛选：时间预设 - 近30天 */
    public static final String INSIGHT_TIME_PRESET_30D = "30d";

    /** 仪表盘筛选：时间预设 - 近90天 */
    public static final String INSIGHT_TIME_PRESET_90D = "90d";

    /** 仪表盘筛选：时间预设 - 自定义 */
    public static final String INSIGHT_TIME_PRESET_CUSTOM = "custom";

    /** 仪表盘筛选：filter 键名 */
    public static final String INSIGHT_FILTER_KEY_FIELD = "field";

    /** 仪表盘筛选：operator 键名 */
    public static final String INSIGHT_FILTER_KEY_OPERATOR = "operator";

    /** 仪表盘筛选：value 键名 */
    public static final String INSIGHT_FILTER_KEY_VALUE = "value";

    // ==================== 输入优化 ====================

    /** 输入优化会话 ID 前缀 */
    public static final String OPTIMIZE_CONVERSATION_PREFIX = "optimize-";

    /** 输入优化提示词模板，{0} 为用户原始输入 */
    public static final String OPTIMIZE_PROMPT_TEMPLATE =
            "请对以下用户输入进行润色优化，使其更清晰、更专业、更有条理，但保持原意不变。仅返回优化后的文本，不要加任何解释。\n\n用户输入：\n{0}";

    // ==================== AI助手对话 ====================

    /** AI助手对话会话ID前缀（统一生成和修改） */
    public static final String INSIGHT_AI_CHAT_CONVERSATION_PREFIX = "insight-ai-chat-";

    /** AI生成仪表盘时Schema检索默认Top-K */
    public static final int INSIGHT_GENERATE_SCHEMA_TOP_K = 10;

    /** AI生成仪表盘默认Schema版本号 */
    public static final String INSIGHT_GENERATE_SCHEMA_VERSION = "1.0";

    /** AI生成仪表盘默认组件宽度 */
    public static final int INSIGHT_GENERATE_DEFAULT_COMPONENT_W = 6;

    /** AI生成仪表盘默认组件高度 */
    public static final int INSIGHT_GENERATE_DEFAULT_COMPONENT_H = 4;

    /** AI生成仪表盘每行组件数 */
    public static final int INSIGHT_GENERATE_COLUMNS_PER_ROW = 2;
}