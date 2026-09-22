package vip.mate.dataagent.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import vip.mate.dataagent.auth.crypto.AesPasswordTypeHandler;

import java.time.LocalDateTime;

/**
 * 用户 Aloudata UID 映射实体
 * <p>
 * 从外部用户系统定时同步「登录名 + 租户 → Aloudata UID」映射关系，
 * 用户问数时可使用映射的 UID 认证，无需手动绑定查询账号；
 * 手动绑定（{@link DatasourceAccountEntity}）优先，本表映射兜底。
 */
@Data
@TableName(value = "dataagent_user_uid_mapping", autoResultMap = true)
public class UserUidMappingEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 登录名（对齐 mate_user.username，同步时按同口径归一化） */
    private String username;

    /** Aloudata 租户 ID（对齐数据源租户配置） */
    private String tenantId;

    /**
     * 昵称（仅展示用，不参与映射命中判定）
     * <p>
     * 优先取源库返回的 nickname 列，未提供或为空时回落 mate_user.nickname。
     */
    private String nickname;

    /**
     * Aloudata UID 认证值（AES-256-GCM 加密存储，读写由 AesPasswordTypeHandler 自动加解密）
     * <p>
     * 列宽 500 约束的是密文（{@code AES:} + base64(iv + 密文 + tag)），
     * 明文 UID 上限约 340 字符。
     */
    @TableField(typeHandler = AesPasswordTypeHandler.class)
    private String aloudataUid;

    /** 状态：0-停用 / 1-启用 */
    private Integer status;

    /** 映射来源：jdbc_sync-定时同步 / manual-手动录入 */
    private String syncSource;

    /** 最近同步时间 */
    private LocalDateTime syncTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;
}
