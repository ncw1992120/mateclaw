package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户 UID 映射状态视图对象
 * <p>
 * 返回当前用户已启用的自动映射概要，供前端数据源页面展示「已自动同步」状态。
 */
@Data
public class UserUidMappingStatusVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Aloudata 租户 ID */
    private String tenantId;

    /** 最近同步时间（yyyy-MM-dd HH:mm:ss） */
    private String syncTime;
}
