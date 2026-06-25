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

    /** Schema 语义检索 RRF 融合参数 k */
    public static final int SCHEMA_SEARCH_RRF_K = 60;

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

    /** Elasticsearch 默认向量检索候选数（numCandidates） */
    public static final int ES_KNN_NUM_CANDIDATES = 100;

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
            "termName", "synonyms", "description", "category", "embeddingText"
    };

    /** 业务术语语义检索默认 Top-K */
    public static final int BUSINESS_TERM_SEARCH_DEFAULT_TOP_K = 10;

    /** 业务术语语义检索默认相似度阈值 */
    public static final double BUSINESS_TERM_SEARCH_DEFAULT_THRESHOLD = 0.3;
}