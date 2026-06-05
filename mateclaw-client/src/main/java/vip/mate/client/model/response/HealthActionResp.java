package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 健康检查修复动作
 */
@Data
public class HealthActionResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 按钮标签 */
    private String label;

    /** 跳转路由 */
    private String route;
}
