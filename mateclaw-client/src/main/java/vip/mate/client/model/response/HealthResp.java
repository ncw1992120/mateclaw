package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 系统健康检查响应
 */
@Data
public class HealthResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 总体状态 (healthy/warning/error) */
    private String overall;

    /** 各项检查列表 */
    private List<HealthCheckResp> checks;
}
