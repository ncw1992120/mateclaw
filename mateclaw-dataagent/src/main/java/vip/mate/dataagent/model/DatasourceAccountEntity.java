package vip.mate.dataagent.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据源用户查询账号绑定实体
 * <p>
 * 每个用户可以为自己绑定的数据源配置独立的查询账号，
 * 查询时优先使用用户自己的查询账号，而非数据源的管理员同步账号。
 */
@Data
@TableName("dataagent_datasource_account")
public class DatasourceAccountEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联数据源 ID */
    private Long datasourceId;

    /** 所属工作区 ID */
    private Long workspaceId;

    /** 用户 ID */
    private Long userId;

    /** 查询用户名 */
    private String queryUsername;

    /** 查询密码（AES 加密存储） */
    private String queryPassword;

    /** 状态：0-停用 / 1-启用 */
    private Integer status;

    /** 最近测试时间 */
    private LocalDateTime lastTestTime;

    /** 最近测试结果 */
    private Boolean lastTestOk;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;
}
