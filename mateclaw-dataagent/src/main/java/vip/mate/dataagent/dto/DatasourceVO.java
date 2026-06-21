package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 数据源视图对象
 */
@Data
public class DatasourceVO {

    private Long id;

    /** 数据源名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 数据源类型 */
    private String sourceType;

    /** 主机地址（兼容旧数据） */
    private String host;

    /** 产品层服务地址（Aloudata anymetrics） */
    private String productHost;

    /** 语义层服务地址（Aloudata semantic） */
    private String semanticHost;

    /** 端口 */
    private Integer port;

    /** 数据库名称 */
    private String databaseName;

    /** 用户名 */
    private String username;

    /** 密码（AES 加密存储） */
    private String password;

    /** 连接参数（JSON） */
    private String connectionParams;

    /** Schema 名称 */
    private String schemaName;

    /** 是否启用 */
    private Boolean enabled;

    /** 最近测试时间 */
    private String lastTestTime;

    /** 最近测试结果 */
    private Boolean lastTestOk;

    /** Schema 发现状态 */
    private String schemaStatus;

    /** 最近 Schema 发现时间 */
    private String lastSchemaDiscoveryTime;

    /** 表数量 */
    private Integer tableCount;

    private String createTime;

    private String updateTime;
}