package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 创建数据源请求
 */
@Data
public class DatasourceCreateRequest {

    /** 数据源名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 数据源类型 */
    private String sourceType;

    /** 主机地址 */
    private String host;

    /** 端口 */
    private Integer port;

    /** 数据库名称/文件路径/接口地址/Topic */
    private String databaseName;

    /** 用户名 */
    private String username;

    /** 密码 */
    private String password;

    /** 连接参数（JSON） */
    private String connectionParams;

    /** Schema 名称 */
    private String schemaName;

    /** 是否启用 */
    private Boolean enabled;
}