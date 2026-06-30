package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 创建/更新数据源用户查询账号请求
 */
@Data
public class DatasourceAccountRequest {

    /** 关联数据源 ID */
    private Long datasourceId;

    /** 查询用户名 */
    private String queryUsername;

    /** 查询密码 */
    private String queryPassword;
}
