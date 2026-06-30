package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 数据源用户查询账号视图对象
 */
@Data
public class DatasourceAccountVO {

    private Long id;

    /** 关联数据源 ID */
    private Long datasourceId;

    /** 数据源名称 */
    private String datasourceName;

    /** 数据源类型 */
    private String datasourceType;

    /** 查询用户名 */
    private String queryUsername;

    /** 状态：0-停用 / 1-启用 */
    private Integer status;

    /** 最近测试时间 */
    private String lastTestTime;

    /** 最近测试结果 */
    private Boolean lastTestOk;

    private String createTime;

    private String updateTime;
}
