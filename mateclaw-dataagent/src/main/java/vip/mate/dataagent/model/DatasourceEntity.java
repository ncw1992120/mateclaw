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

    /** 数据源名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 数据源类型：mysql / postgresql / oracle / snowflake / bigquery / redshift / clickhouse / doris / mongodb / elasticsearch / csv / excel / parquet / api / kafka */
    private String sourceType;

    /** 主机地址 */
    private String host;

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

    /** 是否启用 */
    private Boolean enabled;

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