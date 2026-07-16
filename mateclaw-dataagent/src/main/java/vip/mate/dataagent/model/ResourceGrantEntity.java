package vip.mate.dataagent.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通用资源授权实体
 * <p>
 * 通过一张表管理所有资源的授权关系，避免每种资源一张授权表的膨胀。
 * 支持 skill 授权、发布审批等场景的权限定义。
 */
@Data
@TableName("dataagent_resource_grant")
public class ResourceGrantEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 资源类型：skill / agent / datasource / business_term 等 */
    private String resourceType;

    /** 资源 ID（对应业务表的主键） */
    private Long resourceId;

    /** 所属工作区 ID */
    private Long workspaceId;

    /** 授权类型：role / user / group（按角色/用户/用户组授权） */
    private String grantType;

    /** 被授权者标识：角色名/用户ID/用户组ID */
    private String granteeId;

    /** 权限：use / manage / publish（使用/管理/发布） */
    private String permission;

    /** 授权人用户 ID */
    private Long grantedBy;

    /** 状态：0-已撤销 / 1-生效中 */
    private Integer status;

    /** 过期时间（NULL 表示永久） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime expireTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;
}
