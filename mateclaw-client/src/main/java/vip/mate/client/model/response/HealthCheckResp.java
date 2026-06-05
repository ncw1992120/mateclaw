package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 健康检查项
 */
@Data
public class HealthCheckResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 检查项名称 */
    private String name;

    /** 状态 (healthy/warning/error) */
    private String status;

    /** 描述信息 */
    private String message;

    /** 修复动作 */
    private HealthActionResp action;
}
