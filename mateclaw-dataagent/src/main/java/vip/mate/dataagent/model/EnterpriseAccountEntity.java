package vip.mate.dataagent.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 企业认证影子账号映射实体（dataagent_enterprise_account）
 * <p>
 * 记录本地 mate_user 影子账号与企业身份（领航域账号）的对应关系，
 * 用于运维审计与后续离职禁用联动；影子账号本身仍存于 mate_user 表。
 */
@Data
@TableName("dataagent_enterprise_account")
public class EnterpriseAccountEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 本地影子账号用户名（= 企业侧 principalName，域账号） */
    private String username;

    /** 企业侧唯一标识（领航 content.PRINCIPAL_NAME） */
    private String principalName;

    /** 身份来源：PILOT_UM（领航域账号口令认证） */
    private String source;

    /** 状态：ACTIVE / DISABLED */
    private String status;

    /** 最近企业登录时间 */
    private LocalDateTime lastLoginAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记 */
    private Integer deleted;
}
