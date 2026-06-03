package vip.mate.dataagent.constants;

/**
 * DataAgent 常量定义
 */
public final class DataAgentConstants {

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

    private DataAgentConstants() {
    }
}