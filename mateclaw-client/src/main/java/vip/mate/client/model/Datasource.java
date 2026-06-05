package vip.mate.client.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据源实体
 */
@Data
public class Datasource implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String description;
    private String dbType;
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    private String password;
    private String extraParams;
    private String schemaName;
    private Boolean enabled;
    private LocalDateTime lastTestTime;
    private Boolean lastTestOk;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
