package vip.mate.dataagent.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据源实体
 * <p>
 * 支持关系型数据库、数据仓库、OLAP引擎、NoSQL、文件系统、API接口、消息队列等多源异构接入。
 */
@Data
@TableName("dataagent_datasource")
public class DatasourceEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属工作区 ID */
    private Long workspaceId;

    /** 数据源创建者用户 ID（权限隔离用，列表查询按此字段过滤） */
    private Long ownerId;

    /** 数据源名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 数据源类型：mysql / postgresql / oracle / snowflake / bigquery / redshift / clickhouse / doris / mongodb / elasticsearch / csv / excel / parquet / api / kafka */
    private String sourceType;

    /** 主机地址（通用字段，可作为历史数据兜底） */
    private String host;

    /** 产品层服务地址（Aloudata anymetrics，端口默认 8083） */
    private String productHost;

    /** 语义层服务地址（Aloudata semantic，端口默认 8085） */
    private String semanticHost;

    /** 端口 */
    private Integer port;

    /** 数据库名称/文件路径/接口地址/Topic等 */
    private String databaseName;

    /** 用户名 */
    private String username;

    /** 密码（AES 加密存储） */
    private String password;

    /** 连接参数（JSON 格式，存放额外配置） */
    private String connectionParams;

    /** Schema 名称（PostgreSQL等使用） */
    private String schemaName;

    /** 元数据是否共享（1=同工作区所有用户可见，0=仅 owner 可见） */
    private Boolean metaShared;

    /** 是否启用 */
    private Boolean enabled;

    /** Aloudata 语义层定时同步开关（1=开启，0=关闭） */
    private Boolean aloudataSyncEnabled;

    /** Aloudata 语义层定时同步 cron 表达式（5 段：分 时 日 月 周，秒固定为 0） */
    private String aloudataSyncCron;

    /** 最近一次 Aloudata 语义层同步完成时间（含手动与定时） */
    private LocalDateTime lastAloudataSyncTime;

    /** 最近测试时间 */
    private LocalDateTime lastTestTime;

    /** 最近测试结果 */
    private Boolean lastTestOk;

    /** Schema 发现状态：pending / running / completed / failed */
    private String schemaStatus;

    /** 最近 Schema 发现时间 */
    private LocalDateTime lastSchemaDiscoveryTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;
}